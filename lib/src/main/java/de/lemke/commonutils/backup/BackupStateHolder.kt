/*
 * Copyright 2024-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.lemke.commonutils.backup

import android.net.Uri
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.backup.core.BackupExport
import de.lemke.commonutils.backup.core.BackupReadResult
import de.lemke.commonutils.backup.core.ImportMode
import de.lemke.commonutils.ui.utils.DocumentPick
import de.lemke.commonutils.ui.utils.runContained
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BackupStateHolder"

/** The document of an export; [settled] once the export wrote it or began to delete it, so a later cancellation leaves it alone. */
private class ExportTarget(val uri: Uri) {
    @Volatile var settled = false
}

private sealed interface Zipped {
    class Bytes(val bytes: ByteArray) : Zipped

    data object TooLarge : Zipped

    data object Failed : Zipped
}

private val exportFailed = BackupState.Result(BackupResult.ExportFailed)
private val readFailed = BackupState.Result(BackupResult.ReadFailed)
private val restoreFailed = BackupState.Result(BackupResult.RestoreFailed)

/**
 * Runs the export and import of a ViewModel through [engine] and [documents] in [scope], one at a time, and holds the
 * [BackupState] of the latest one. The CPU work (zip of an export, parse, upgrade, validate, signature) runs on
 * [dispatcher]; an import unzips the stream of [documents] as it arrives, on the dispatcher of [documents].
 *
 * A ViewModel creates it with `viewModelScope` and exposes [state]. Only [BackupState.Working] blocks: while an export or
 * import runs, [export], [import] and the picker callbacks do nothing. A document the export picker created while
 * something runs is deleted. A read keeps its decoded entries inside [BackupState.Preview] and
 * [BackupState.ConfirmReplace]. After [scope] is cancelled the state stays [BackupState.Working], and an export in
 * flight deletes its document. Tests drive the holder with `FakeBackupDocuments` from the testFixtures.
 */
public class BackupStateHolder(
    private val scope: CoroutineScope,
    private val engine: BackupEngine,
    private val documents: BackupDocuments,
    private val dispatcher: CoroutineDispatcher,
) {
    private val mutableState = MutableStateFlow<BackupState>(BackupState.Idle)

    /** The state of the latest export or import. */
    public val state: StateFlow<BackupState> = mutableState.asStateFlow()

    /** Asks for the target of a zip named after [appLabel]; ends in [BackupState.PickExportTarget]. */
    public fun export(appLabel: String) {
        publishUnlessWorking(BackupState.PickExportTarget("${appLabel}_backup"))
    }

    /** Writes the export into the document of [pick], the result of the picker that [BackupState.PickExportTarget] opened. */
    public fun onExportTargetPicked(pick: DocumentPick) {
        when (pick) {
            DocumentPick.Canceled -> mutableState.update { if (it is BackupState.PickExportTarget) BackupState.Idle else it }
            DocumentPick.MissingUri -> publishUnlessWorking(exportFailed)
            is DocumentPick.Created -> {
                val target = ExportTarget(pick.uri)
                val started = launchWork(exportFailed, discardOnCancel = target) { exportTo(target) }
                if (!started) scope.launch { documents.delete(pick.uri) }
            }
        }
    }

    /** Asks for the file to import; ends in [BackupState.PickImportFile]. */
    public fun import() {
        publishUnlessWorking(BackupState.PickImportFile)
    }

    /** Reads the file at [uri], the result of the picker that [BackupState.PickImportFile] opened; null means canceled. */
    public fun onImportFilePicked(uri: Uri?) {
        if (uri == null) {
            mutableState.update { if (it == BackupState.PickImportFile) BackupState.Idle else it }
        } else {
            launchWork(readFailed) { readFrom(uri) }
        }
    }

    /**
     * Restores the previewed backup in [mode]. [ImportMode.REPLACE] first ends in [BackupState.ConfirmReplace]; call
     * [confirmReplace] after the user accepted the warning. Does nothing unless [BackupState.Preview] shows.
     */
    public fun restore(mode: ImportMode) {
        val preview = mutableState.value as? BackupState.Preview ?: return
        when (mode) {
            ImportMode.REPLACE -> mutableState.value = BackupState.ConfirmReplace(preview.preview, preview.ready)
            ImportMode.MERGE -> launchWork(restoreFailed) { restoreFrom(preview.ready, ImportMode.MERGE) }
        }
    }

    /** Replaces the data with the previewed backup. Does nothing unless [BackupState.ConfirmReplace] shows. */
    public fun confirmReplace() {
        val confirm = mutableState.value as? BackupState.ConfirmReplace ?: return
        launchWork(restoreFailed) { restoreFrom(confirm.ready, ImportMode.REPLACE) }
    }

    /** Drops a pending picker request, preview or warning. Does nothing while [BackupState.Working]. */
    public fun cancel() {
        mutableState.update { if (it == BackupState.Working) it else BackupState.Idle }
    }

    /** Resets the state to [BackupState.Idle] if it still equals [state]. */
    public fun onHandled(state: BackupState) {
        mutableState.compareAndSet(state, BackupState.Idle)
    }

    private fun publishUnlessWorking(next: BackupState) {
        mutableState.update { if (it == BackupState.Working) it else next }
    }

    private fun launchWork(
        failure: BackupState.Result,
        discardOnCancel: ExportTarget? = null,
        work: suspend () -> BackupState,
    ): Boolean {
        if (mutableState.getAndUpdate { BackupState.Working } == BackupState.Working) return false
        // ATOMIC: a job canceled before it starts must still discard its document.
        scope.launch(start = CoroutineStart.ATOMIC) {
            mutableState.value =
                try {
                    runContained(TAG, "Backup work", failure) { withContext(dispatcher) { work() } }
                } catch (e: CancellationException) {
                    discardOnCancel?.takeUnless { it.settled }?.let { discard(it.uri) }
                    throw e
                }
        }
        return true
    }

    private suspend fun exportTo(target: ExportTarget): BackupState {
        val result =
            when (val zipped = runContained<Zipped>(TAG, "Backup export", Zipped.Failed) { zipped() }) {
                Zipped.Failed -> BackupResult.ExportFailed
                Zipped.TooLarge -> BackupResult.ExportTooLarge
                is Zipped.Bytes -> if (documents.write(target.uri, zipped.bytes)) BackupResult.Exported else BackupResult.ExportFailed
            }
        target.settled = true
        if (result != BackupResult.Exported) discard(target.uri)
        return BackupState.Result(result)
    }

    private suspend fun discard(uri: Uri) = withContext(NonCancellable) { documents.delete(uri) }

    private suspend fun zipped(): Zipped =
        when (val export = engine.export()) {
            BackupExport.TooLarge -> Zipped.TooLarge
            is BackupExport.Files -> Zipped.Bytes(ByteArrayOutputStream().also { ZipBackupContainer.write(export.files, it) }.toByteArray())
        }

    private suspend fun readFrom(uri: Uri): BackupState {
        val context = currentCoroutineContext()
        val zip = documents.read(uri) { ZipBackupContainer.read(it, ensureActive = context::ensureActive) }
        return when (zip) {
            null, ZipRead.Invalid, ZipRead.TooLarge -> readFailed
            is ZipRead.Files -> engine.read(zip.files).toState()
        }
    }

    private suspend fun restoreFrom(
        ready: BackupReadResult.Ready,
        mode: ImportMode,
    ): BackupState = BackupState.Result(BackupResult.Restored(engine.restore(ready, mode)))
}

private fun BackupReadResult.toState(): BackupState =
    when (this) {
        is BackupReadResult.Refused -> BackupState.Result(BackupResult.Refused(reason))
        is BackupReadResult.Ready -> BackupState.Preview(preview, this)
    }
