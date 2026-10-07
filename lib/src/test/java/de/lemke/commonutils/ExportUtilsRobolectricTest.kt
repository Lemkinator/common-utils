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
package de.lemke.commonutils

import android.content.ActivityNotFoundException
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.LaunchOutcome
import de.lemke.commonutils.ui.utils.exportBitmap
import de.lemke.commonutils.ui.utils.saveBitmapToDirectory
import de.lemke.commonutils.ui.utils.saveBitmapToUri
import de.lemke.commonutils.ui.utils.toast
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

private val bitmap: Bitmap get() = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

private val timestampedPng = Regex("""test_\d{4}_\d{2}_\d{2}_\d{2}_\d{2}_\d{2}\.png""")

private fun publicDirectory(type: String): File = Environment.getExternalStoragePublicDirectory(type).apply { mkdirs() }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportUtilsRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    // ── exportBitmap ──────────────────────────────────────────────────────────

    @Test
    fun `exportBitmap launches the document picker for a timestamped PNG`() {
        val launcher = RecordingIntentLauncher()

        ctx.exportBitmap("test", launcher) shouldBe LaunchOutcome.Started

        val intent = launcher.launched.single()
        intent.action shouldBe Intent.ACTION_CREATE_DOCUMENT
        intent.categories shouldContainExactly setOf(Intent.CATEGORY_OPENABLE)
        intent.type shouldBe "image/png"
        intent.getStringExtra(Intent.EXTRA_TITLE)!! shouldMatch timestampedPng
    }

    @Test
    fun `exportBitmap without a document picker shows the not-supported toast and returns Failed`() {
        val launcher = RecordingIntentLauncher(ActivityNotFoundException("no picker"))

        ctx.exportBitmap("test", launcher) shouldBe LaunchOutcome.Failed

        launcher.launched.shouldBeEmpty()
        ShadowToast.getTextOfLatestToast() shouldBe "Error: Saving content is not supported on your device."
    }

    @Test
    fun `exportBitmap whose launch the latch drops returns Dropped without a toast`() {
        val launcher = RecordingIntentLauncher(admits = false)

        ctx.exportBitmap("test", launcher) shouldBe LaunchOutcome.Dropped

        launcher.launched.shouldBeEmpty()
        ShadowToast.getLatestToast() shouldBe null
    }

    // ── saveBitmapToDirectory ─────────────────────────────────────────────────

    @Test
    fun `saveBitmapToDirectory writes a PNG to Pictures`() =
        runTest {
            val directory = publicDirectory(Environment.DIRECTORY_PICTURES)

            saveBitmapToDirectory(SaveLocation.PICTURES, bitmap, "test") shouldBe
                BitmapSaveResult.Saved(SaveLocation.PICTURES)

            directory.listFiles()!!.single().name shouldMatch timestampedPng
        }

    @Test
    fun `saveBitmapToDirectory writes a PNG to DCIM`() =
        runTest {
            val directory = publicDirectory(Environment.DIRECTORY_DCIM)

            saveBitmapToDirectory(SaveLocation.DCIM, bitmap, "test") shouldBe
                BitmapSaveResult.Saved(SaveLocation.DCIM)

            directory.listFiles()!!.single().name shouldMatch timestampedPng
        }

    @Test
    fun `saveBitmapToDirectory writes a PNG to Downloads`() =
        runTest {
            val directory = publicDirectory(Environment.DIRECTORY_DOWNLOADS)

            saveBitmapToDirectory(SaveLocation.DOWNLOADS, bitmap, "test") shouldBe
                BitmapSaveResult.Saved(SaveLocation.DOWNLOADS)

            directory.listFiles()!!.single().name shouldMatch timestampedPng
        }

    @Test
    fun `saveBitmapToDirectory reports EncodingFailed when the bitmap cannot be encoded and leaves no file`() =
        runTest {
            val directory = publicDirectory(Environment.DIRECTORY_DCIM)
            val failing = mockk<Bitmap>()
            every { failing.compress(any(), any(), any<OutputStream>()) } returns false

            saveBitmapToDirectory(SaveLocation.DCIM, failing, "test") shouldBe BitmapSaveResult.EncodingFailed

            directory.listFiles()!!.shouldBeEmpty()
        }

    @Test
    fun `saveBitmapToDirectory reports WriteFailed when the directory is a file`() =
        runTest {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloads.deleteRecursively()
            downloads.parentFile!!.mkdirs()
            downloads.createNewFile().shouldBeTrue()

            saveBitmapToDirectory(SaveLocation.DOWNLOADS, bitmap, "test") shouldBe BitmapSaveResult.WriteFailed
        }

    @Test
    fun `saveBitmapToDirectory reports WriteFailed when encoding throws and leaves no file`() =
        runTest {
            val directory = publicDirectory(Environment.DIRECTORY_PICTURES)
            val throwing = mockk<Bitmap>()
            every { throwing.compress(any(), any(), any<OutputStream>()) } throws IOException("disk full")

            saveBitmapToDirectory(SaveLocation.PICTURES, throwing, "test") shouldBe BitmapSaveResult.WriteFailed

            directory.listFiles()!!.shouldBeEmpty()
        }

    @Test
    fun `saveBitmapToDirectory writes on the given IO dispatcher`() =
        runTest {
            val directory = publicDirectory(Environment.DIRECTORY_PICTURES)
            val io = HeldDispatcher()
            val save = async { saveBitmapToDirectory(SaveLocation.PICTURES, bitmap, "test", io) }
            runCurrent()

            save.isCompleted.shouldBeFalse()
            directory.listFiles()!!.shouldBeEmpty()

            io.held.removeFirst().run()
            save.await() shouldBe BitmapSaveResult.Saved(SaveLocation.PICTURES)
            directory.listFiles()!!.single().name shouldMatch timestampedPng
        }

    @Test
    fun `saveBitmapToDirectory reports NeedsPicker for CUSTOM`() =
        runTest {
            saveBitmapToDirectory(SaveLocation.CUSTOM, bitmap, "test") shouldBe BitmapSaveResult.NeedsPicker
        }

    // ── toast(BitmapSaveResult) ───────────────────────────────────────────────

    @Test
    fun `toast Saved names the location`() {
        ctx.toast(BitmapSaveResult.Saved(SaveLocation.PICTURES))
        ShadowToast.getTextOfLatestToast() shouldBe "Image saved: Pictures"
    }

    @Test
    fun `toast Saved to CUSTOM shows the plain confirmation`() {
        ctx.toast(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
        ShadowToast.getTextOfLatestToast() shouldBe "Image saved"
    }

    @Test
    fun `toast EncodingFailed shows the saving error`() {
        ctx.toast(BitmapSaveResult.EncodingFailed)
        ShadowToast.getTextOfLatestToast() shouldBe "Error saving image"
    }

    @Test
    fun `toast WriteFailed shows the file error`() {
        ctx.toast(BitmapSaveResult.WriteFailed)
        ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
    }

    // ── SaveLocation ──────────────────────────────────────────────────────────

    @Test
    fun `publicDirectoryType maps every location`() {
        SaveLocation.entries.associateWith { it.publicDirectoryType } shouldBe
            mapOf(
                SaveLocation.CUSTOM to null,
                SaveLocation.DOWNLOADS to Environment.DIRECTORY_DOWNLOADS,
                SaveLocation.PICTURES to Environment.DIRECTORY_PICTURES,
                SaveLocation.DCIM to Environment.DIRECTORY_DCIM,
            )
    }

    @Test
    fun `needsPicker holds only for CUSTOM on API 30+`() {
        SaveLocation.entries.filter { it.needsPicker } shouldBe listOf(SaveLocation.CUSTOM)
    }

    // ── saveBitmapToUri ───────────────────────────────────────────────────────

    @Test
    fun `saveBitmapToUri reports Canceled for the null uri of a canceled picker and the caller shows no toast`() =
        runTest {
            val result = ctx.saveBitmapToUri(null, bitmap, createdDocument = true)

            when (result) {
                is BitmapSaveResult.Finished -> ctx.toast(result)
                BitmapSaveResult.Canceled -> Unit
            }

            result shouldBe BitmapSaveResult.Canceled
            ShadowToast.shownToastCount() shouldBe 0
        }

    @Test
    fun `saveBitmapToUri reports WriteFailed when bitmap is null and writes nothing`() =
        runTest {
            val file = File(ctx.cacheDir, "null_bitmap_test.png")

            ctx.saveBitmapToUri(Uri.fromFile(file), null, createdDocument = false) shouldBe BitmapSaveResult.WriteFailed

            file.exists().shouldBeFalse()
        }

    @Test
    fun `saveBitmapToUri writes the PNG and reports Saved to CUSTOM`() =
        runTest {
            val file = File(ctx.cacheDir, "export_test.png").also { it.createNewFile() }

            ctx.saveBitmapToUri(Uri.fromFile(file), bitmap, createdDocument = false) shouldBe BitmapSaveResult.Saved(SaveLocation.CUSTOM)

            (file.length() > 0).shouldBeTrue()
        }

    @Test
    fun `saveBitmapToUri writes on the given IO dispatcher`() =
        runTest {
            val file = File(ctx.cacheDir, "export_held.png")
            val io = HeldDispatcher()
            val save = async { ctx.saveBitmapToUri(Uri.fromFile(file), bitmap, createdDocument = false, io) }
            runCurrent()

            save.isCompleted.shouldBeFalse()
            file.exists().shouldBeFalse()

            io.held.removeFirst().run()
            save.await() shouldBe BitmapSaveResult.Saved(SaveLocation.CUSTOM)
            (file.length() > 0).shouldBeTrue()
        }

    @Test
    fun `saveBitmapToUri reports EncodingFailed when the bitmap cannot be encoded`() =
        runTest {
            val file = File(ctx.cacheDir, "export_fail.png").also { it.createNewFile() }
            val failing = mockk<Bitmap>()
            every { failing.compress(any(), any(), any<OutputStream>()) } returns false

            ctx.saveBitmapToUri(Uri.fromFile(file), failing, createdDocument = false) shouldBe BitmapSaveResult.EncodingFailed
        }

    @Test
    fun `saveBitmapToUri reports WriteFailed without a provider for the uri`() =
        runTest {
            val uri = Uri.parse("content://de.lemke.nonexistent/data/1")

            ctx.saveBitmapToUri(uri, bitmap, createdDocument = false) shouldBe BitmapSaveResult.WriteFailed
        }

    @Test
    fun `saveBitmapToUri reports WriteFailed when neither writing nor deleting a created document reaches a provider`() =
        runTest {
            val uri = Uri.parse("content://de.lemke.nonexistent/data/1")

            ctx.saveBitmapToUri(uri, bitmap, createdDocument = true) shouldBe BitmapSaveResult.WriteFailed
        }

    @Test
    fun `saveBitmapToUri reports WriteFailed for a SecurityException from openOutputStream`() =
        runTest {
            val uri = Uri.parse("content://de.lemke.provider/revoked/1")
            shadowOf(ctx.contentResolver).registerOutputStreamSupplier(uri) { throw SecurityException("permission revoked") }

            ctx.saveBitmapToUri(uri, bitmap, createdDocument = false) shouldBe BitmapSaveResult.WriteFailed
        }

    @Test
    fun `saveBitmapToUri reports WriteFailed for an IllegalArgumentException from the resolver`() =
        runTest {
            val uri = Uri.parse("content://de.lemke.provider/unknown/1")
            shadowOf(ctx.contentResolver).registerOutputStreamSupplier(uri) { throw IllegalArgumentException("Unknown URI") }

            ctx.saveBitmapToUri(uri, bitmap, createdDocument = false) shouldBe BitmapSaveResult.WriteFailed
        }

    @Test
    fun `saveBitmapToUri reports WriteFailed for an UnsupportedOperationException from the resolver`() =
        runTest {
            val uri = Uri.parse("content://de.lemke.provider/readonly/1")
            shadowOf(ctx.contentResolver).registerOutputStreamSupplier(uri) { throw UnsupportedOperationException("Writing not supported") }

            ctx.saveBitmapToUri(uri, bitmap, createdDocument = false) shouldBe BitmapSaveResult.WriteFailed
        }

    @Test
    fun `saveBitmapToUri reports WriteFailed for a null stream`() =
        runTest {
            Robolectric.buildContentProvider(NoFileContentProvider::class.java).create("de.lemke.nofile")
            val uri = Uri.parse("content://de.lemke.nofile/1")

            ctx.saveBitmapToUri(uri, bitmap, createdDocument = false) shouldBe BitmapSaveResult.WriteFailed
        }

    @Test
    fun `saveBitmapToUri deletes the document when the bitmap cannot be encoded`() =
        runTest {
            val provider = documentProvider()
            val failing = mockk<Bitmap>()
            every { failing.compress(any(), any(), any<OutputStream>()) } returns false

            ctx.saveBitmapToUri(provider.uri, failing, createdDocument = true) shouldBe BitmapSaveResult.EncodingFailed

            provider.deleted shouldContainExactly listOf(provider.uri)
        }

    @Test
    fun `saveBitmapToUri deletes the document when the write throws`() =
        runTest {
            val provider = documentProvider()
            val throwing = mockk<Bitmap>()
            every { throwing.compress(any(), any(), any<OutputStream>()) } throws IOException("disk full")

            ctx.saveBitmapToUri(provider.uri, throwing, createdDocument = true) shouldBe BitmapSaveResult.WriteFailed

            provider.deleted shouldContainExactly listOf(provider.uri)
        }

    @Test
    fun `saveBitmapToUri keeps the document it wrote`() =
        runTest {
            val provider = documentProvider()

            ctx.saveBitmapToUri(provider.uri, bitmap, createdDocument = true) shouldBe BitmapSaveResult.Saved(SaveLocation.CUSTOM)

            provider.deleted.shouldBeEmpty()
            (provider.file.length() > 0).shouldBeTrue()
        }

    @Test
    fun `saveBitmapToUri deletes the created document when the bitmap is null`() =
        runTest {
            val provider = documentProvider()

            ctx.saveBitmapToUri(provider.uri, null, createdDocument = true) shouldBe BitmapSaveResult.WriteFailed

            provider.deleted shouldContainExactly listOf(provider.uri)
        }

    @Test
    fun `saveBitmapToUri keeps an existing document when the bitmap cannot be encoded`() =
        runTest {
            val provider = documentProvider()
            val failing = mockk<Bitmap>()
            every { failing.compress(any(), any(), any<OutputStream>()) } returns false

            ctx.saveBitmapToUri(provider.uri, failing, createdDocument = false) shouldBe BitmapSaveResult.EncodingFailed

            provider.deleted.shouldBeEmpty()
            provider.file.exists().shouldBeTrue()
        }

    @Test
    fun `saveBitmapToUri keeps an existing document when the bitmap is null`() =
        runTest {
            val provider = documentProvider()

            ctx.saveBitmapToUri(provider.uri, null, createdDocument = false) shouldBe BitmapSaveResult.WriteFailed

            provider.deleted.shouldBeEmpty()
        }

    @Test
    fun `saveBitmapToUri reports the failure when deleting the document fails`() =
        runTest {
            val provider = documentProvider().apply { deleteFailure = FileNotFoundException("gone") }
            val failing = mockk<Bitmap>()
            every { failing.compress(any(), any(), any<OutputStream>()) } returns false

            ctx.saveBitmapToUri(provider.uri, failing, createdDocument = true) shouldBe BitmapSaveResult.EncodingFailed

            provider.deleted shouldContainExactly listOf(provider.uri)
        }

    private fun documentProvider(): RecordingDocumentsProvider = RecordingDocumentsProvider.create(ctx)
}

private class NoFileContentProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor? = null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ExportUtilsSdk29RobolectricTest {
    @Test
    fun `needsPicker holds for every location up to API 29`() {
        SaveLocation.entries.filter { it.needsPicker } shouldBe SaveLocation.entries
    }

    @Test
    fun `saveBitmapToDirectory reports NeedsPicker for Downloads on API 29 and writes nothing`() =
        runTest {
            val directory = publicDirectory(Environment.DIRECTORY_DOWNLOADS)

            saveBitmapToDirectory(SaveLocation.DOWNLOADS, bitmap, "test") shouldBe BitmapSaveResult.NeedsPicker

            directory.listFiles()!!.shouldBeEmpty()
        }
}
