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
import androidx.core.net.toUri
import de.lemke.commonutils.backup.core.BackupApp
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.backup.core.BackupRefusal
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.commonutils.backup.core.ImportMode
import de.lemke.commonutils.backup.core.Item
import de.lemke.commonutils.backup.core.MAX_SECTION_ENTRIES
import de.lemke.commonutils.backup.core.MemorySection
import de.lemke.commonutils.backup.core.MergeableMemorySection
import de.lemke.commonutils.backup.core.RestoreReport
import de.lemke.commonutils.backup.core.SectionInfo
import de.lemke.commonutils.backup.core.SectionPreview
import de.lemke.commonutils.backup.core.SectionReport
import de.lemke.commonutils.backup.core.backupFilesOf
import de.lemke.commonutils.backup.core.entriesJson
import de.lemke.commonutils.backup.core.item
import de.lemke.commonutils.ui.utils.DocumentPick
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val target: Uri = "content://documents/export".toUri()
private val source: Uri = "content://documents/import".toUri()

private object DirectTransaction : BackupTransaction {
    override suspend fun <R> run(block: suspend () -> R): R = block()
}

private object FailingTransaction : BackupTransaction {
    override suspend fun <R> run(block: suspend () -> R): R = error("database is gone")
}

private class GatedDocuments(private val inner: FakeBackupDocuments, val gate: CompletableDeferred<Unit>) : BackupDocuments {
    override suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    ): Boolean = gate.await().let { inner.write(uri, bytes) }

    override suspend fun <T : Any> read(
        uri: Uri,
        consume: (InputStream) -> T,
    ): T? = gate.await().let { inner.read(uri, consume) }

    override suspend fun delete(uri: Uri) = inner.delete(uri)
}

private class CancelingDocuments(private val inner: FakeBackupDocuments, private val cancel: () -> Unit) : BackupDocuments {
    override suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    ): Boolean = inner.write(uri, bytes).also { cancel() }

    override suspend fun <T : Any> read(
        uri: Uri,
        consume: (InputStream) -> T,
    ): T? = inner.read(uri, consume)

    override suspend fun delete(uri: Uri) = inner.delete(uri)
}

private class EndlessDocuments(private val prefix: ByteArray) : BackupDocuments {
    var served = 0L

    override suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    ): Boolean = false

    override suspend fun <T : Any> read(
        uri: Uri,
        consume: (InputStream) -> T,
    ): T? =
        consume(
            object : InputStream() {
                override fun read(): Int = error("The container reads in blocks")

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int {
                    for (i in 0 until len) b[off + i] = if (served + i < prefix.size) prefix[(served + i).toInt()] else 0
                    served += len
                    return len
                }
            },
        )

    override suspend fun delete(uri: Uri) = Unit
}

private class ThrowingSection : MemorySection() {
    override suspend fun exportEntries(): List<Item> = error("cannot read")
}

private fun zip(files: Map<String, ByteArray>): ByteArray =
    ByteArrayOutputStream()
        .also {
            ZipBackupContainer.write(files, it)
        }.toByteArray()

private fun engineOf(
    vararg sections: BackupSection<*>,
    transaction: BackupTransaction = DirectTransaction,
): BackupEngine = BackupEngine(sections.toSet(), transaction, BackupApp("oneurl", 100), { "2026-10-27T14:30:00+01:00" })

private fun TestScope.holderOf(
    engine: BackupEngine,
    documents: BackupDocuments,
): BackupStateHolder = BackupStateHolder(this, engine, documents, UnconfinedTestDispatcher(testScheduler))

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupStateHolderRobolectricTest {
    private val documents = FakeBackupDocuments()

    private fun backupZip(vararg lines: String) = zip(backupFilesOf(SectionInfo("items", 1, lines.size) to entriesJson(*lines)))

    @Test
    fun `export moves Idle to PickExportTarget with the base file name`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val holder = holderOf(engineOf(MemorySection()), documents)

            holder.export("Notes")

            holder.state.value shouldBe BackupState.PickExportTarget("Notes_backup")
        }

    @Test
    fun `a picked target writes the zip and ends in Exported`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val holder = holderOf(engineOf(MemorySection(store = mutableListOf(Item("a", 1)))), documents)

            holder.onExportTargetPicked(DocumentPick.Created(target))

            holder.state.value shouldBe BackupState.Result(BackupResult.Exported)
            val written = ZipBackupContainer.read(ByteArrayInputStream(documents.files.getValue(target)))
            written.shouldBeInstanceOf<ZipRead.Files>().files.keys shouldBe setOf("manifest.json", "sections/items.json")
        }

    @Test
    fun `a failed write deletes the document and ends in ExportFailed`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            documents.failWrite = true
            val holder = holderOf(engineOf(MemorySection()), documents)

            holder.onExportTargetPicked(DocumentPick.Created(target))

            holder.state.value shouldBe BackupState.Result(BackupResult.ExportFailed)
            documents.deleted shouldContainExactly listOf(target)
        }

    @Test
    fun `an export that throws deletes the document and ends in ExportFailed`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val holder = holderOf(engineOf(ThrowingSection()), documents)

            holder.onExportTargetPicked(DocumentPick.Created(target))

            holder.state.value shouldBe BackupState.Result(BackupResult.ExportFailed)
            documents.deleted shouldContainExactly listOf(target)
        }

    @Test
    fun `an export over a limit deletes the document and ends in ExportTooLarge`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val tooMany = MutableList(MAX_SECTION_ENTRIES + 1) { Item("k$it") }
            val holder = holderOf(engineOf(MemorySection(store = tooMany)), documents)

            holder.onExportTargetPicked(DocumentPick.Created(target))

            holder.state.value shouldBe BackupState.Result(BackupResult.ExportTooLarge)
            documents.deleted shouldContainExactly listOf(target)
            documents.files shouldBe emptyMap()
        }

    @Test
    fun `canceling the scope during the write deletes the created document`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val exportScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
            val holder =
                BackupStateHolder(
                    exportScope,
                    engineOf(MemorySection()),
                    GatedDocuments(documents, CompletableDeferred()),
                    UnconfinedTestDispatcher(testScheduler),
                )
            holder.onExportTargetPicked(DocumentPick.Created(target))
            holder.state.value shouldBe BackupState.Working

            exportScope.cancel()

            documents.deleted shouldContainExactly listOf(target)
        }

    @Test
    fun `canceling the scope right after a successful write keeps the document`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val exportScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
            val holder =
                BackupStateHolder(
                    exportScope,
                    engineOf(MemorySection()),
                    CancelingDocuments(documents) { exportScope.cancel() },
                    UnconfinedTestDispatcher(testScheduler),
                )

            holder.onExportTargetPicked(DocumentPick.Created(target))

            documents.files.keys shouldBe setOf(target)
            documents.deleted shouldBe emptyList()
        }

    @Test
    fun `canceling the scope right after a failed write deletes the document once`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            documents.failWrite = true
            val exportScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
            val holder =
                BackupStateHolder(
                    exportScope,
                    engineOf(MemorySection()),
                    CancelingDocuments(documents) { exportScope.cancel() },
                    UnconfinedTestDispatcher(testScheduler),
                )

            holder.onExportTargetPicked(DocumentPick.Created(target))

            documents.deleted shouldContainExactly listOf(target)
        }

    @Test
    fun `canceling the scope before the export job is first dispatched deletes the created document`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val exportScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob(coroutineContext.job))
            val holder = BackupStateHolder(exportScope, engineOf(MemorySection()), documents, StandardTestDispatcher(testScheduler))
            holder.onExportTargetPicked(DocumentPick.Created(target))

            exportScope.cancel()
            testScheduler.advanceUntilIdle()

            documents.deleted shouldContainExactly listOf(target)
        }

    @Test
    fun `canceling the scope during an import deletes no document`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val importScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
            val holder =
                BackupStateHolder(
                    importScope,
                    engineOf(MemorySection()),
                    GatedDocuments(documents, CompletableDeferred()),
                    UnconfinedTestDispatcher(testScheduler),
                )
            holder.onImportFilePicked(source)

            importScope.cancel()

            documents.deleted shouldContainExactly emptyList()
        }

    @Test
    fun `canceling the scope before the export work starts deletes the created document`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val exportScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
            val holder = BackupStateHolder(exportScope, engineOf(MemorySection()), documents, StandardTestDispatcher(testScheduler))
            holder.onExportTargetPicked(DocumentPick.Created(target))

            exportScope.cancel()
            testScheduler.advanceUntilIdle()

            documents.deleted shouldContainExactly listOf(target)
        }

    @Test
    fun `the work runs on the injected dispatcher`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            documents.files[source] = backupZip(item("a"))
            val holder = BackupStateHolder(this, engineOf(MemorySection()), documents, StandardTestDispatcher(testScheduler))

            holder.onImportFilePicked(source)
            holder.state.value shouldBe BackupState.Working

            testScheduler.advanceUntilIdle()
            holder.state.value.shouldBeInstanceOf<BackupState.Preview>()
        }

    @Test
    fun `a canceled pick returns to Idle`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val holder = holderOf(engineOf(MemorySection()), documents)
            holder.export("Notes")

            holder.onExportTargetPicked(DocumentPick.Canceled)

            holder.state.value shouldBe BackupState.Idle
        }

    @Test
    fun `a pick without a document ends in ExportFailed`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val holder = holderOf(engineOf(MemorySection()), documents)

            holder.onExportTargetPicked(DocumentPick.MissingUri)

            holder.state.value shouldBe BackupState.Result(BackupResult.ExportFailed)
        }

    @Test
    fun `import then a picked file ends in Preview with the skipped count`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            documents.files[source] = backupZip(item("a"), item("bad", -1), item("b"))
            val holder = holderOf(engineOf(MergeableMemorySection()), documents)

            holder.import()
            holder.state.value shouldBe BackupState.PickImportFile
            holder.onImportFilePicked(source)

            val preview =
                holder.state.value
                    .shouldBeInstanceOf<BackupState.Preview>()
                    .preview
            preview.sections shouldContainExactly listOf(SectionPreview("items", 2, 1))
            preview.modes shouldBe setOf(ImportMode.MERGE, ImportMode.REPLACE)
        }

    @Test
    fun `import of a file with an unknown section ends in Preview naming it`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val info = SectionInfo("extra", 3, 0)
            documents.files[source] = zip(backupFilesOf(SectionInfo("items", 1, 0) to entriesJson(), info to entriesJson()))
            val holder = holderOf(engineOf(MemorySection()), documents)

            holder.onImportFilePicked(source)

            holder.state.value
                .shouldBeInstanceOf<BackupState.Preview>()
                .preview.skippedSections shouldContainExactly listOf("extra")
        }

    @Test
    fun `a newer section ends in Refused and writes nothing`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val store = mutableListOf(Item("keep"))
            documents.files[source] =
                zip(backupFilesOf(SectionInfo("items", 2, 1) to entriesJson(item("a"))))
            val holder = holderOf(engineOf(MemorySection(store = store)), documents)

            holder.onImportFilePicked(source)

            holder.state.value shouldBe BackupState.Result(BackupResult.Refused(BackupRefusal.NewerSection("items", 2)))
            store shouldBe listOf(Item("keep"))
        }

    @Test
    fun `an unreadable, non-zip or oversized file ends in ReadFailed`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val holder = holderOf(engineOf(MemorySection()), documents)
            val readFailed = BackupState.Result(BackupResult.ReadFailed)

            holder.onImportFilePicked(source)
            holder.state.value shouldBe readFailed

            holder.onHandled(readFailed)
            documents.files[source] = "not a zip".encodeToByteArray()
            holder.onImportFilePicked(source)
            holder.state.value shouldBe readFailed

            holder.onHandled(readFailed)
            documents.files[source] = zip(mapOf("sections/items.json" to ByteArray(16 * 1024 * 1024 + 1)))
            holder.onImportFilePicked(source)
            holder.state.value shouldBe readFailed

            holder.onHandled(readFailed)
            documents.files[source] = backupZip(item("a")) + ByteArray(MAX_ARCHIVE_BYTES.toInt())
            holder.onImportFilePicked(source)
            holder.state.value shouldBe readFailed
        }

    @Test
    fun `an import stops streaming a file that never ends at the archive limit`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val endless = EndlessDocuments(backupZip(item("a")))
            val holder = holderOf(engineOf(MemorySection()), endless)

            holder.onImportFilePicked(source)

            holder.state.value shouldBe BackupState.Result(BackupResult.ReadFailed)
            endless.served shouldBeLessThan MAX_ARCHIVE_BYTES + 16 * 1024
        }

    @Test
    fun `a canceled file pick returns to Idle`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val holder = holderOf(engineOf(MemorySection()), documents)
            holder.import()

            holder.onImportFilePicked(null)

            holder.state.value shouldBe BackupState.Idle
        }

    @Test
    fun `restore REPLACE moves to ConfirmReplace and writes nothing`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val store = mutableListOf(Item("local"))
            documents.files[source] = backupZip(item("a"))
            val holder = holderOf(engineOf(MemorySection(store = store)), documents)
            holder.onImportFilePicked(source)

            holder.restore(ImportMode.REPLACE)

            holder.state.value.shouldBeInstanceOf<BackupState.ConfirmReplace>()
            store shouldBe listOf(Item("local"))
        }

    @Test
    fun `confirmReplace runs Replace and ends in Restored`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val store = mutableListOf(Item("local"))
            documents.files[source] = backupZip(item("a"))
            val holder = holderOf(engineOf(MemorySection(store = store)), documents)
            holder.onImportFilePicked(source)
            holder.restore(ImportMode.REPLACE)

            holder.confirmReplace()

            store shouldBe listOf(Item("a"))
            holder.state.value shouldBe
                BackupState.Result(
                    BackupResult.Restored(
                        RestoreReport(
                            ImportMode.REPLACE,
                            listOf(SectionReport("items", added = 1, updated = 0, kept = 0, removed = 1, skipped = 0)),
                        ),
                    ),
                )
        }

    @Test
    fun `restore MERGE runs Merge and ends in Restored`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val store = mutableListOf(Item("local", 1))
            documents.files[source] = backupZip(item("a", 2))
            val holder = holderOf(engineOf(MergeableMemorySection(store = store)), documents)
            holder.onImportFilePicked(source)

            holder.restore(ImportMode.MERGE)

            store shouldBe listOf(Item("local", 1), Item("a", 2))
            holder.state.value
                .shouldBeInstanceOf<BackupState.Result>()
                .result
                .shouldBeInstanceOf<BackupResult.Restored>()
        }

    @Test
    fun `a throwing transaction ends in RestoreFailed`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            documents.files[source] = backupZip(item("a"))
            val holder = holderOf(engineOf(MergeableMemorySection(), transaction = FailingTransaction), documents)
            holder.onImportFilePicked(source)

            holder.restore(ImportMode.MERGE)

            holder.state.value shouldBe BackupState.Result(BackupResult.RestoreFailed)
        }

    @Test
    fun `restore and confirmReplace do nothing outside their states`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val holder = holderOf(engineOf(MergeableMemorySection()), documents)

            holder.restore(ImportMode.MERGE)
            holder.confirmReplace()

            holder.state.value shouldBe BackupState.Idle
        }

    @Test
    fun `cancel drops a preview`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            documents.files[source] = backupZip(item("a"))
            val holder = holderOf(engineOf(MemorySection()), documents)
            holder.onImportFilePicked(source)

            holder.cancel()

            holder.state.value shouldBe BackupState.Idle
        }

    @Test
    fun `onHandled with a stale state changes nothing`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val holder = holderOf(engineOf(MemorySection()), documents)
            holder.onExportTargetPicked(DocumentPick.MissingUri)

            holder.onHandled(BackupState.Result(BackupResult.Exported))
            holder.state.value shouldBe BackupState.Result(BackupResult.ExportFailed)

            holder.onHandled(BackupState.Result(BackupResult.ExportFailed))
            holder.state.value shouldBe BackupState.Idle
        }

    @Test
    fun `a second export or import while Working is ignored and a created document is deleted`(): Unit =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val holder = holderOf(engineOf(MemorySection()), GatedDocuments(documents, gate))
            holder.onExportTargetPicked(DocumentPick.Created(target))
            holder.state.value shouldBe BackupState.Working

            holder.export("Notes")
            holder.import()
            holder.cancel()
            holder.onImportFilePicked(null)
            holder.onExportTargetPicked(DocumentPick.Canceled)
            holder.onExportTargetPicked(DocumentPick.Created(source))
            holder.state.value shouldBe BackupState.Working
            documents.deleted shouldContainExactly listOf(source)

            gate.complete(Unit)
            holder.state.value shouldBe BackupState.Result(BackupResult.Exported)
        }
}
