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
package de.lemke.commonutils.ui.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.MainThread
import de.lemke.commonutils.R
import de.lemke.commonutils.data.SaveLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where [launchBitmapExport] shares a [BitmapExport.Share]. */
enum class ShareTarget {
    /** The system share sheet; see [shareBitmap]. */
    SHARE_SHEET,

    /** Samsung Quick Share if installed, the system share sheet otherwise; see [quickShareBitmap]. */
    QUICK_SHARE,
}

/**
 * State of the save, copy or share of a bitmap that a [BitmapExportStateHolder] runs.
 *
 * A RESUMED collector passes each [Result] to [launchBitmapExport] and reports it to [BitmapExportStateHolder.onHandled]
 * once that returns true.
 */
sealed interface BitmapExport {
    /** A finished export that the screen acts on and then reports handled. */
    sealed interface Result : BitmapExport

    /** No export runs and no result waits. */
    data object Idle : BitmapExport

    /** An export runs; the holder ignores new saves, copies and shares until it finishes. */
    data object Running : BitmapExport

    /** The save location needs the document picker; [launchBitmapExport] opens it for a PNG named after [fileName]. */
    data class OpenPicker(
        val fileName: String,
    ) : Result

    /** A save ended with [result]; [launchBitmapExport] shows its message. */
    data class SaveFinished(
        val result: BitmapSaveResult.Finished,
    ) : Result

    /** The bitmap was written for the clipboard; [launchBitmapExport] copies [clip]. */
    data class Copy(
        val clip: ClipData,
    ) : Result

    /** Writing the bitmap for the clipboard failed; [launchBitmapExport] shows the error toast. */
    data object CopyFailed : Result

    /** The bitmap was written for a share; [launchBitmapExport] shares [file] through [target]. */
    data class Share(
        val file: BitmapShareFile.Written,
        val target: ShareTarget = ShareTarget.SHARE_SHEET,
    ) : Result

    /** Writing the bitmap for a share failed; [launchBitmapExport] shows the error toast. */
    data object ShareFailed : Result
}

/**
 * Runs the bitmap exports of a ViewModel through [exporter] in [scope], one at a time, and holds the [BitmapExport]
 * state of the latest one.
 *
 * A ViewModel creates it with `viewModelScope` and exposes [state]. While an export is [BitmapExport.Running], [save],
 * [copy] and [share] do nothing. Tests drive it with `FakeBitmapExporter` from the testFixtures.
 */
class BitmapExportStateHolder(
    private val scope: CoroutineScope,
    private val exporter: BitmapExporter,
) {
    private val mutableState = MutableStateFlow<BitmapExport>(BitmapExport.Idle)

    /** The state of the latest export. */
    val state: StateFlow<BitmapExport> = mutableState.asStateFlow()

    /**
     * Saves [bitmap] as a PNG named after [fileName] to [location]; a location that needs the document picker ends in
     * [BitmapExport.OpenPicker].
     */
    fun save(
        bitmap: Bitmap,
        fileName: String,
        location: SaveLocation,
    ) {
        start {
            when (val result = exporter.saveToDirectory(location, bitmap, fileName)) {
                is BitmapSaveResult.Finished -> BitmapExport.SaveFinished(result)
                BitmapSaveResult.NeedsPicker -> BitmapExport.OpenPicker(fileName)
            }
        }
    }

    /**
     * Writes [bitmap] into the document of [pick], the result of the picker that [BitmapExport.OpenPicker] opened.
     *
     * A canceled pick changes nothing. A pick without a document, or a null [bitmap], ends in a failed
     * [BitmapExport.SaveFinished]; a null [bitmap] also deletes the created document.
     */
    fun onDocumentPicked(
        pick: DocumentPick,
        bitmap: Bitmap?,
    ) {
        when (pick) {
            DocumentPick.Canceled -> Unit
            DocumentPick.MissingUri -> mutableState.value = BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
            is DocumentPick.Created -> launchExport { exporter.saveToCreatedDocument(pick.uri, bitmap).toExport() }
        }
    }

    /** Writes [bitmap] for the clipboard; ends in [BitmapExport.Copy] or [BitmapExport.CopyFailed]. */
    fun copy(bitmap: Bitmap) {
        start { exporter.createClip(bitmap)?.let(BitmapExport::Copy) ?: BitmapExport.CopyFailed }
    }

    /**
     * Writes [bitmap] for a share through [target]; ends in [BitmapExport.Share] or [BitmapExport.ShareFailed], or in
     * [BitmapExport.Idle] if the launch latch dropped the write.
     */
    fun share(
        bitmap: Bitmap,
        target: ShareTarget = ShareTarget.SHARE_SHEET,
    ) {
        start {
            when (val file = exporter.createShareFile(bitmap)) {
                is BitmapShareFile.Written -> BitmapExport.Share(file, target)
                BitmapShareFile.Failed -> BitmapExport.ShareFailed
                BitmapShareFile.Dropped -> null
            }
        }
    }

    /** Resets the state to [BitmapExport.Idle] if it still holds [result]. */
    fun onHandled(result: BitmapExport.Result) {
        mutableState.update { if (it == result) BitmapExport.Idle else it }
    }

    private fun start(work: suspend () -> BitmapExport.Result?) {
        if (mutableState.value != BitmapExport.Running) launchExport(work)
    }

    private fun launchExport(work: suspend () -> BitmapExport.Result?) {
        mutableState.value = BitmapExport.Running
        scope.launch { mutableState.value = work() ?: BitmapExport.Idle }
    }

    private fun BitmapSaveResult.UriResult.toExport(): BitmapExport.Result? =
        when (this) {
            is BitmapSaveResult.Finished -> BitmapExport.SaveFinished(this)
            BitmapSaveResult.Canceled -> null
        }
}

/**
 * Acts on [result]: opens the document picker through [picker], shares, copies, or shows the message of a save or the
 * error toast of a failed copy or share. A failure shows its toast once.
 *
 * Call it from a RESUMED collector of [BitmapExportStateHolder.state] and report [result] to
 * [BitmapExportStateHolder.onHandled] once it returns true.
 * @return true once [result] is handled; false while the launch latch drops its launch, so the result stays pending
 * until the activity resumes.
 */
@MainThread
fun Context.launchBitmapExport(
    result: BitmapExport.Result,
    picker: SingleLaunchResultLauncher<Intent>,
): Boolean =
    when (result) {
        is BitmapExport.OpenPicker -> {
            exportBitmap(result.fileName, picker).isSettled
        }

        is BitmapExport.Share -> {
            when (result.target) {
                ShareTarget.SHARE_SHEET -> shareBitmap(result.file)
                ShareTarget.QUICK_SHARE -> quickShareBitmap(result.file)
            }.isSettled
        }

        is BitmapExport.SaveFinished -> {
            true.also { toast(result.result) }
        }

        is BitmapExport.Copy -> {
            true.also { copyToClipboard(result.clip) }
        }

        BitmapExport.CopyFailed, BitmapExport.ShareFailed -> {
            true.also { toast(R.string.commonutils_error_share_content_not_supported_on_device) }
        }
    }
