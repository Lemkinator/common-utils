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
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import java.io.OutputStream
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

        ctx.exportBitmap("test", launcher).shouldBeTrue()

        val intent = launcher.launched.single()
        intent.action shouldBe Intent.ACTION_CREATE_DOCUMENT
        intent.categories shouldContainExactly setOf(Intent.CATEGORY_OPENABLE)
        intent.type shouldBe "image/png"
        intent.getStringExtra(Intent.EXTRA_TITLE)!! shouldMatch timestampedPng
    }

    @Test
    fun `exportBitmap without a document picker shows the not-supported toast and returns false`() {
        val launcher = RecordingIntentLauncher(ActivityNotFoundException("no picker"))

        ctx.exportBitmap("test", launcher).shouldBeFalse()

        launcher.launched.shouldBeEmpty()
        ShadowToast.getTextOfLatestToast() shouldBe "Error: Saving content is not supported on your device."
    }

    // ── saveBitmapToDirectory ─────────────────────────────────────────────────

    @Test
    fun `saveBitmapToDirectory writes a PNG to Pictures`() =
        runTest {
            val directory = publicDirectory(Environment.DIRECTORY_PICTURES)

            saveBitmapToDirectory(SaveLocation.PICTURES, bitmap, "test").shouldBeInstanceOf<BitmapSaveResult.Saved>().location shouldBe
                SaveLocation.PICTURES

            directory.listFiles()!!.single().name shouldMatch timestampedPng
        }

    @Test
    fun `saveBitmapToDirectory writes a PNG to DCIM`() =
        runTest {
            val directory = publicDirectory(Environment.DIRECTORY_DCIM)

            saveBitmapToDirectory(SaveLocation.DCIM, bitmap, "test").shouldBeInstanceOf<BitmapSaveResult.Saved>().location shouldBe
                SaveLocation.DCIM

            directory.listFiles()!!.single().name shouldMatch timestampedPng
        }

    @Test
    fun `saveBitmapToDirectory writes a PNG to Downloads`() =
        runTest {
            val directory = publicDirectory(Environment.DIRECTORY_DOWNLOADS)

            saveBitmapToDirectory(SaveLocation.DOWNLOADS, bitmap, "test").shouldBeInstanceOf<BitmapSaveResult.Saved>().location shouldBe
                SaveLocation.DOWNLOADS

            directory.listFiles()!!.single().name shouldMatch timestampedPng
        }

    @Test
    fun `saveBitmapToDirectory reports EncodingFailed when the bitmap cannot be encoded`() =
        runTest {
            publicDirectory(Environment.DIRECTORY_DCIM)
            val failing = mockk<Bitmap>()
            every { failing.compress(any(), any(), any<OutputStream>()) } returns false

            saveBitmapToDirectory(SaveLocation.DCIM, failing, "test") shouldBeSameInstanceAs BitmapSaveResult.EncodingFailed
        }

    @Test
    fun `saveBitmapToDirectory reports WriteFailed when the directory is a file`() =
        runTest {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloads.deleteRecursively()
            downloads.parentFile!!.mkdirs()
            downloads.createNewFile().shouldBeTrue()

            saveBitmapToDirectory(SaveLocation.DOWNLOADS, bitmap, "test") shouldBeSameInstanceAs BitmapSaveResult.WriteFailed
        }

    @Test
    fun `saveBitmapToDirectory reports WriteFailed when encoding throws`() =
        runTest {
            publicDirectory(Environment.DIRECTORY_PICTURES)
            val throwing = mockk<Bitmap>()
            every { throwing.compress(any(), any(), any<OutputStream>()) } throws IOException("disk full")

            saveBitmapToDirectory(SaveLocation.PICTURES, throwing, "test") shouldBeSameInstanceAs BitmapSaveResult.WriteFailed
        }

    @Test
    fun `saveBitmapToDirectory reports NeedsPicker for CUSTOM`() =
        runTest {
            saveBitmapToDirectory(SaveLocation.CUSTOM, bitmap, "test") shouldBeSameInstanceAs BitmapSaveResult.NeedsPicker
        }

    // ── toast(BitmapSaveResult) ───────────────────────────────────────────────

    @Test
    fun `toast Saved names the location`() {
        ctx.toast(BitmapSaveResult.Saved(SaveLocation.PICTURES))
        ShadowToast.getTextOfLatestToast() shouldBe "Image saved: Pictures"
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

    @Test
    fun `toast NeedsPicker shows the not-supported error`() {
        ctx.toast(BitmapSaveResult.NeedsPicker)
        ShadowToast.getTextOfLatestToast() shouldBe "Error: Saving content is not supported on your device."
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
    fun `saveBitmapToUri returns false when uri is null`() {
        ctx.saveBitmapToUri(null, bitmap).shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
    }

    @Test
    fun `saveBitmapToUri returns false when bitmap is null`() {
        val uri = Uri.fromFile(File(ctx.cacheDir, "null_bitmap_test.png"))
        ctx.saveBitmapToUri(uri, null).shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
    }

    @Test
    fun `saveBitmapToUri writes the PNG and confirms with a toast`() {
        val file = File(ctx.cacheDir, "export_test.png").also { it.createNewFile() }
        ctx.saveBitmapToUri(Uri.fromFile(file), bitmap).shouldBeTrue()
        (file.length() > 0).shouldBeTrue()
        ShadowToast.getTextOfLatestToast() shouldBe "Image saved"
    }

    @Test
    fun `saveBitmapToUri reports an encoding failure`() {
        val file = File(ctx.cacheDir, "export_fail.png").also { it.createNewFile() }
        val failing = mockk<Bitmap>()
        every { failing.compress(any(), any(), any<OutputStream>()) } returns false
        ctx.saveBitmapToUri(Uri.fromFile(file), failing).shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe "Error saving image"
    }

    @Test
    fun `saveBitmapToUri without a provider for the uri shows the file error`() {
        ctx.saveBitmapToUri(Uri.parse("content://de.lemke.nonexistent/data/1"), bitmap).shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
    }

    @Test
    fun `saveBitmapToUri SecurityException from openOutputStream returns false`() {
        val uri = Uri.parse("content://de.lemke.provider/revoked/1")
        shadowOf(ctx.contentResolver).registerOutputStreamSupplier(uri) { throw SecurityException("permission revoked") }
        ctx.saveBitmapToUri(uri, bitmap).shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
    }

    @Test
    fun `saveBitmapToUri IllegalArgumentException from the resolver shows the error toast and returns false`() {
        val uri = Uri.parse("content://de.lemke.provider/unknown/1")
        shadowOf(ctx.contentResolver).registerOutputStreamSupplier(uri) { throw IllegalArgumentException("Unknown URI") }
        ctx.saveBitmapToUri(uri, bitmap).shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
    }

    @Test
    fun `saveBitmapToUri UnsupportedOperationException from the resolver shows the error toast and returns false`() {
        val uri = Uri.parse("content://de.lemke.provider/readonly/1")
        shadowOf(ctx.contentResolver).registerOutputStreamSupplier(uri) { throw UnsupportedOperationException("Writing not supported") }
        ctx.saveBitmapToUri(uri, bitmap).shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
    }

    @Test
    fun `saveBitmapToUri null stream shows the error toast and returns false`() {
        Robolectric.buildContentProvider(NoFileContentProvider::class.java).create("de.lemke.nofile")
        ctx.saveBitmapToUri(Uri.parse("content://de.lemke.nofile/1"), bitmap).shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
    }
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

            saveBitmapToDirectory(SaveLocation.DOWNLOADS, bitmap, "test") shouldBeSameInstanceAs BitmapSaveResult.NeedsPicker

            directory.listFiles()!!.shouldBeEmpty()
        }
}
