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
import kotlinx.coroutines.CompletableDeferred
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

    private fun TestScope.holder() = BitmapExportStateHolder(backgroundScope, exporter)

    @Test
    fun `save to a public directory ends in SaveFinished with the saved location`() =
        runTest {
            val holder = holder()

            holder.save(bitmap, "icon", SaveLocation.PICTURES)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.PICTURES))
            exporter.calls shouldContainExactly listOf(Call.SaveToDirectory(SaveLocation.PICTURES, bitmap, "icon"))
        }

    @Test
    fun `save that fails ends in SaveFinished with the failure`() =
        runTest {
            val holder = holder()
            exporter.directoryResult = BitmapSaveResult.WriteFailed

            holder.save(bitmap, "icon", SaveLocation.DOWNLOADS)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

    @Test
    fun `save to a location that needs the picker ends in OpenPicker with the file name`() =
        runTest {
            val holder = holder()

            holder.save(bitmap, "icon", SaveLocation.CUSTOM)
            runCurrent()

            holder.state.value shouldBe BitmapExport.OpenPicker("icon")
        }

    @Test
    fun `an export is Running until the exporter returns and ignores every other export meanwhile`() =
        runTest {
            val holder = holder()
            val gate = CompletableDeferred<Unit>()
            val clip = ClipData.newPlainText("label", "text")
            exporter.gate = gate
            exporter.clip = clip

            holder.copy(bitmap)
            runCurrent()
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
    fun `a handled result lets the next export start`() =
        runTest {
            val holder = holder()
            exporter.shareFile = written

            holder.save(bitmap, "icon", SaveLocation.PICTURES)
            runCurrent()
            holder.onHandled(BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.PICTURES)))
            holder.share(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Share(written, ShareTarget.SHARE_SHEET)
            exporter.calls shouldContainExactly
                listOf(Call.SaveToDirectory(SaveLocation.PICTURES, bitmap, "icon"), Call.CreateShareFile(bitmap))
        }

    @Test
    fun `a created document while an export runs is deleted and the export keeps running`() =
        runTest {
            val holder = holder()
            val gate = CompletableDeferred<Unit>()
            exporter.gate = gate

            holder.save(bitmap, "icon", SaveLocation.PICTURES)
            runCurrent()
            holder.onDocumentPicked(DocumentPick.Created(document), bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Running
            exporter.calls shouldContainExactly
                listOf(
                    Call.SaveToDirectory(SaveLocation.PICTURES, bitmap, "icon"),
                    Call.SaveToCreatedDocument(document, null),
                )
            gate.complete(Unit)
            runCurrent()
            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.PICTURES))
        }

    @Test
    fun `a pick without a document while an export runs is ignored`() =
        runTest {
            val holder = holder()
            val gate = CompletableDeferred<Unit>()
            exporter.gate = gate

            holder.copy(bitmap)
            runCurrent()
            holder.onDocumentPicked(DocumentPick.MissingUri, bitmap)

            holder.state.value shouldBe BitmapExport.Running
        }

    @Test
    fun `an export after a waiting result replaces it`() =
        runTest {
            val holder = holder()
            exporter.shareFile = written
            holder.save(bitmap, "icon", SaveLocation.CUSTOM)
            runCurrent()

            holder.share(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Share(written, ShareTarget.SHARE_SHEET)
            exporter.calls shouldContainExactly
                listOf(Call.SaveToDirectory(SaveLocation.CUSTOM, bitmap, "icon"), Call.CreateShareFile(bitmap))
        }

    @Test
    fun `an export ignored while running leaves the running export and its result`() =
        runTest {
            val holder = holder()
            val gate = CompletableDeferred<Unit>()
            exporter.shareFile = written
            exporter.gate = gate
            holder.share(bitmap)
            runCurrent()

            holder.save(bitmap, "icon", SaveLocation.PICTURES)
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Share(written, ShareTarget.SHARE_SHEET)
            exporter.calls shouldContainExactly listOf(Call.CreateShareFile(bitmap))
        }

    @Test
    fun `a result the launch latch dropped does not strand the holder`() =
        runTest {
            val holder = holder()
            exporter.shareFile = written
            holder.share(bitmap)
            runCurrent()
            holder.state.value shouldBe BitmapExport.Share(written, ShareTarget.SHARE_SHEET)

            holder.copy(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.CopyFailed
        }

    @Test
    fun `a created document after a waiting result replaces it`() =
        runTest {
            val holder = holder()
            holder.save(bitmap, "icon", SaveLocation.CUSTOM)
            runCurrent()

            holder.onDocumentPicked(DocumentPick.Created(document), bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
            exporter.calls shouldContainExactly
                listOf(Call.SaveToDirectory(SaveLocation.CUSTOM, bitmap, "icon"), Call.SaveToCreatedDocument(document, bitmap))
        }

    @Test
    fun `a pick without a document after a waiting result replaces it`() =
        runTest {
            val holder = holder()
            holder.save(bitmap, "icon", SaveLocation.CUSTOM)
            runCurrent()

            holder.onDocumentPicked(DocumentPick.MissingUri, bitmap)

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

    @Test
    fun `a canceled pick keeps a waiting result`() =
        runTest {
            val holder = holder()
            holder.save(bitmap, "icon", SaveLocation.CUSTOM)
            runCurrent()

            holder.onDocumentPicked(DocumentPick.Canceled, bitmap)

            holder.state.value shouldBe BitmapExport.OpenPicker("icon")
        }

    @Test
    fun `a handled result lets the picked document be written`() =
        runTest {
            val holder = holder()
            holder.save(bitmap, "icon", SaveLocation.CUSTOM)
            runCurrent()
            holder.onHandled(BitmapExport.OpenPicker("icon"))

            holder.onDocumentPicked(DocumentPick.Created(document), bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
        }

    @Test
    fun `a canceled pick changes nothing`() =
        runTest {
            val holder = holder()

            holder.onDocumentPicked(DocumentPick.Canceled, bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Idle
            exporter.calls.shouldBeEmpty()
        }

    @Test
    fun `a pick without a document ends in a failed save without writing`() =
        runTest {
            val holder = holder()

            holder.onDocumentPicked(DocumentPick.MissingUri, bitmap)

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
            exporter.calls.shouldBeEmpty()
        }

    @Test
    fun `a created document receives the bitmap and ends in a custom save`() =
        runTest {
            val holder = holder()

            holder.onDocumentPicked(DocumentPick.Created(document), bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
            exporter.calls shouldContainExactly listOf(Call.SaveToCreatedDocument(document, bitmap))
        }

    @Test
    fun `a created document without a bitmap ends in a failed save and is deleted`() =
        runTest {
            val holder = holder()

            holder.onDocumentPicked(DocumentPick.Created(document), null)
            runCurrent()

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
            exporter.calls shouldContainExactly listOf(Call.SaveToCreatedDocument(document, null))
        }

    @Test
    fun `a created document whose save reports Canceled ends Idle`() =
        runTest {
            val holder = holder()
            exporter.documentResult = BitmapSaveResult.Canceled

            holder.onDocumentPicked(DocumentPick.Created(document), bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Idle
        }

    @Test
    fun `copy without a clip ends in CopyFailed`() =
        runTest {
            val holder = holder()

            holder.copy(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.CopyFailed
        }

    @Test
    fun `share through Quick Share carries its target`() =
        runTest {
            val holder = holder()
            exporter.shareFile = written

            holder.share(bitmap, ShareTarget.QUICK_SHARE)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Share(written, ShareTarget.QUICK_SHARE)
        }

    @Test
    fun `share whose file cannot be written ends in ShareFailed`() =
        runTest {
            val holder = holder()
            exporter.shareFile = BitmapShareFile.Failed

            holder.share(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.ShareFailed
        }

    @Test
    fun `share whose write the latch dropped ends Idle`() =
        runTest {
            val holder = holder()
            exporter.shareFile = BitmapShareFile.Dropped

            holder.share(bitmap)
            runCurrent()

            holder.state.value shouldBe BitmapExport.Idle
        }

    @Test
    fun `onHandled with the current result resets to Idle`() =
        runTest {
            val holder = holder()
            holder.onDocumentPicked(DocumentPick.MissingUri, bitmap)

            holder.onHandled(BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed))

            holder.state.value shouldBe BitmapExport.Idle
        }

    @Test
    fun `onHandled with an older result keeps the current one`() =
        runTest {
            val holder = holder()
            holder.onDocumentPicked(DocumentPick.MissingUri, bitmap)

            holder.onHandled(BitmapExport.CopyFailed)

            holder.state.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }
}
