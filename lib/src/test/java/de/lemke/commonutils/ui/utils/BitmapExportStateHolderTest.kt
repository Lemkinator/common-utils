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
import android.net.Uri
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.FakeBitmapExporter.Call
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BitmapExportStateHolderTest {
    private val exporter = FakeBitmapExporter()
    private val bitmap = pngBitmap()
    private val document: Uri = Uri.parse("content://de.lemke.commonutils.test.documents/document/1")
    private val written = BitmapShareFile.Written(Uri.parse("content://de.lemke.commonutils.test.fileprovider/share/icon.png"))
    private val broken = IllegalStateException("broken")

    private fun TestScope.holder() = BitmapExportStateHolder(backgroundScope, exporter)

    private fun TestScope.runningHolder(): Pair<BitmapExportStateHolder, CompletableDeferred<Unit>> {
        val gate = CompletableDeferred<Unit>()
        exporter.gate = gate
        val holder = holder()
        holder.copy(bitmap)
        runCurrent()
        return holder to gate
    }

    private fun TestScope.resultHolder(): BitmapExportStateHolder {
        val holder = holder()
        holder.save(bitmap, "icon", SaveLocation.CUSTOM)
        runCurrent()
        return holder
    }

    @Test
    fun `save while Idle becomes Running and calls the exporter`() =
        runTest {
            val holder = holder()
            exporter.gate = CompletableDeferred()

            holder.save(bitmap, "icon", SaveLocation.PICTURES)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Running
            exporter.calls shouldContainExactly listOf(Call.SaveToDirectory(SaveLocation.PICTURES, bitmap, "icon"))
        }

    @Test
    fun `save copy and share while Running are ignored`() =
        runTest {
            val clip = ClipData.newPlainText("label", "text")
            exporter.clip = clip
            val (holder, gate) = runningHolder()

            holder.save(bitmap, "icon", SaveLocation.PICTURES)
            holder.share(bitmap)
            holder.copy(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Running
            exporter.calls shouldContainExactly listOf(Call.CreateClip(bitmap))
            gate.complete(Unit)
            runCurrent()
            holder.state.value shouldBe BitmapExport.Copy(clip)
        }

    @Test
    fun `save copy and share while a Result waits replace it and call the exporter`() =
        runTest {
            val holder = resultHolder()
            exporter.shareFile = written

            holder.share(bitmap)
            runCurrent()
            holder.state.value shouldBe BitmapExport.Share(written, ShareTarget.SHARE_SHEET)

            holder.copy(bitmap)
            runCurrent()
            holder.state.value shouldBe BitmapExport.CopyFailed

            holder.save(bitmap, "icon", SaveLocation.PICTURES)
            runCurrent()
            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.PICTURES))

            exporter.calls shouldContainExactly
                listOf(
                    Call.SaveToDirectory(SaveLocation.CUSTOM, bitmap, "icon"),
                    Call.CreateShareFile(bitmap),
                    Call.CreateClip(bitmap),
                    Call.SaveToDirectory(SaveLocation.PICTURES, bitmap, "icon"),
                )
        }

    @Test
    fun `a created document with a bitmap while Idle becomes Running and is saved`() =
        runTest {
            val holder = holder()
            exporter.gate = CompletableDeferred()

            holder.onDocumentPicked(DocumentPick.Created(document), bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Running
            exporter.calls shouldContainExactly listOf(Call.SaveToCreatedDocument(document, bitmap))
        }

    @Test
    fun `a created document while Running is deleted and the export keeps running`() =
        runTest {
            val (holder, gate) = runningHolder()

            holder.onDocumentPicked(DocumentPick.Created(document), bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Running
            exporter.calls shouldContainExactly listOf(Call.CreateClip(bitmap), Call.DeleteCreatedDocument(document))
            gate.complete(Unit)
            runCurrent()
            exporter.deletedDocuments shouldContainExactly listOf(document)
            holder.state.value shouldBe BitmapExport.CopyFailed
        }

    @Test
    fun `a created document with a bitmap while a Result waits replaces it and is saved`() =
        runTest {
            val holder = resultHolder()

            holder.onDocumentPicked(DocumentPick.Created(document), bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
            exporter.calls shouldContainExactly
                listOf(Call.SaveToDirectory(SaveLocation.CUSTOM, bitmap, "icon"), Call.SaveToCreatedDocument(document, bitmap))
        }

    @Test
    fun `a created document without a bitmap while Idle ends in a failed save and is deleted`() =
        runTest {
            val holder = holder()

            holder.onDocumentPicked(DocumentPick.Created(document), null)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
            exporter.calls shouldContainExactly listOf(Call.SaveToCreatedDocument(document, null))
            exporter.deletedDocuments shouldContainExactly listOf(document)
        }

    @Test
    fun `a pick without a document while Idle ends in a failed save without writing`() =
        runTest {
            val holder = holder()

            holder.onDocumentPicked(DocumentPick.MissingUri, bitmap)

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
            exporter.calls.shouldBeEmpty()
        }

    @Test
    fun `a pick without a document while Running is ignored`() =
        runTest {
            val (holder, _) = runningHolder()

            holder.onDocumentPicked(DocumentPick.MissingUri, bitmap)

            holder.state.value shouldBe BitmapExport.Running
        }

    @Test
    fun `a pick without a document while a Result waits replaces it`() =
        runTest {
            val holder = resultHolder()

            holder.onDocumentPicked(DocumentPick.MissingUri, bitmap)

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

    @Test
    fun `a canceled pick while Idle changes nothing`() =
        runTest {
            val holder = holder()

            holder.onDocumentPicked(DocumentPick.Canceled, bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Idle
            exporter.calls.shouldBeEmpty()
        }

    @Test
    fun `a canceled pick while Running changes nothing`() =
        runTest {
            val (holder, _) = runningHolder()

            holder.onDocumentPicked(DocumentPick.Canceled, bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Running
            exporter.calls shouldContainExactly listOf(Call.CreateClip(bitmap))
        }

    @Test
    fun `a canceled pick while a Result waits keeps it`() =
        runTest {
            val holder = resultHolder()

            holder.onDocumentPicked(DocumentPick.Canceled, bitmap)

            holder.state.value shouldBe BitmapExport.OpenPicker("icon")
        }

    @Test
    fun `a save that returns Saved ends in SaveFinished with the saved location`() =
        runTest {
            val holder = holder()

            holder.save(bitmap, "icon", SaveLocation.PICTURES)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.PICTURES))
        }

    @Test
    fun `a save that returns WriteFailed ends in SaveFinished with the failure`() =
        runTest {
            val holder = holder()
            exporter.directoryResult = BitmapSaveResult.WriteFailed

            holder.save(bitmap, "icon", SaveLocation.DOWNLOADS)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

    @Test
    fun `a save that returns NeedsPicker ends in OpenPicker with the file name`() =
        runTest {
            val holder = holder()

            holder.save(bitmap, "icon", SaveLocation.CUSTOM)
            runCurrent()

            holder.state.value shouldBe BitmapExport.OpenPicker("icon")
        }

    @Test
    fun `a copy that returns a clip ends in Copy`() =
        runTest {
            val holder = holder()
            val clip = ClipData.newPlainText("label", "text")
            exporter.clip = clip

            holder.copy(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Copy(clip)
        }

    @Test
    fun `a copy without a clip ends in CopyFailed`() =
        runTest {
            val holder = holder()

            holder.copy(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.CopyFailed
        }

    @Test
    fun `a share that returns a file ends in Share with its target`() =
        runTest {
            val holder = holder()
            exporter.shareFile = written

            holder.share(bitmap, ShareTarget.QUICK_SHARE)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Share(written, ShareTarget.QUICK_SHARE)
        }

    @Test
    fun `a share that returns Failed ends in ShareFailed`() =
        runTest {
            val holder = holder()
            exporter.shareFile = BitmapShareFile.Failed

            holder.share(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.ShareFailed
        }

    @Test
    fun `a share that returns Dropped ends Idle`() =
        runTest {
            val holder = holder()
            exporter.shareFile = BitmapShareFile.Dropped

            holder.share(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Idle
        }

    @Test
    fun `a created document save that returns Canceled ends Idle`() =
        runTest {
            val holder = holder()
            exporter.documentResult = BitmapSaveResult.Canceled

            holder.onDocumentPicked(DocumentPick.Created(document), bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Idle
        }

    @Test
    fun `a save whose exporter throws ends in a failed SaveFinished`() =
        runTest {
            val holder = holder()
            exporter.exception = broken

            holder.save(bitmap, "icon", SaveLocation.PICTURES)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

    @Test
    fun `a created document save whose exporter throws ends in a failed SaveFinished`() =
        runTest {
            val holder = holder()
            exporter.exception = broken

            holder.onDocumentPicked(DocumentPick.Created(document), bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

    @Test
    fun `a copy whose exporter throws ends in CopyFailed`() =
        runTest {
            val holder = holder()
            exporter.exception = broken

            holder.copy(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.CopyFailed
        }

    @Test
    fun `a share whose exporter throws ends in ShareFailed`() =
        runTest {
            val holder = holder()
            exporter.exception = broken

            holder.share(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.ShareFailed
        }

    @Test
    fun `an exporter that throws a CancellationException while the scope lives ends in the failure Result`() =
        runTest {
            val holder = holder()
            exporter.exception = CancellationException("timeout")

            holder.share(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.ShareFailed
        }

    @Test
    fun `a throwing delete of a created document keeps the export running`() =
        runTest {
            val (holder, gate) = runningHolder()
            exporter.gate = null
            exporter.exception = broken

            holder.onDocumentPicked(DocumentPick.Created(document), bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Running
            exporter.calls shouldContainExactly listOf(Call.CreateClip(bitmap), Call.DeleteCreatedDocument(document))
            exporter.deletedDocuments.shouldBeEmpty()
            gate.complete(Unit)
            runCurrent()
            holder.state.value shouldBe BitmapExport.CopyFailed
        }

    @Test
    fun `a canceled export rethrows the cancellation and publishes no failure`() =
        runTest {
            val job = Job()
            val holder = BitmapExportStateHolder(CoroutineScope(coroutineContext + job), exporter)
            exporter.gate = CompletableDeferred()
            holder.copy(bitmap)
            runCurrent()

            job.cancel()
            runCurrent()

            holder.state.value shouldBe BitmapExport.Running
        }

    @Test
    fun `onHandled while Idle is a no-op`() =
        runTest {
            val holder = holder()

            holder.onHandled(BitmapExport.CopyFailed)

            holder.state.value shouldBe BitmapExport.Idle
        }

    @Test
    fun `onHandled while Running is a no-op`() =
        runTest {
            val (holder, _) = runningHolder()

            holder.onHandled(BitmapExport.CopyFailed)

            holder.state.value shouldBe BitmapExport.Running
        }

    @Test
    fun `onHandled with the current Result resets to Idle`() =
        runTest {
            val holder = holder()
            holder.onDocumentPicked(DocumentPick.MissingUri, bitmap)

            holder.onHandled(BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed))

            holder.state.value shouldBe BitmapExport.Idle
        }

    @Test
    fun `onHandled with an older Result keeps the current one`() =
        runTest {
            val holder = holder()
            holder.onDocumentPicked(DocumentPick.MissingUri, bitmap)

            holder.onHandled(BitmapExport.CopyFailed)

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }
}
