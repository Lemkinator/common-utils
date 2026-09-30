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
import androidx.activity.result.ActivityResultLauncher
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.exportBitmap
import de.lemke.commonutils.ui.utils.saveBitmapToUri
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.OutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportUtilsRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val bitmap: Bitmap get() = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    @Test
    fun `saveBitmapToUri returns false when uri is null`() {
        ctx.saveBitmapToUri(null, bitmap).shouldBeFalse()
    }

    @Test
    fun `saveBitmapToUri returns false when bitmap is null`() {
        ctx.saveBitmapToUri(null, null).shouldBeFalse()
    }

    @Test
    fun `exportBitmap returns false when launcher is null and saveLocation is CUSTOM`() {
        ctx.exportBitmap(SaveLocation.CUSTOM, bitmap, "test", null).shouldBeFalse()
    }

    @Test
    fun `exportBitmap with non-null launcher and CUSTOM launches picker and returns true`() {
        val launcher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
        ctx.exportBitmap(SaveLocation.CUSTOM, bitmap, "test", launcher).shouldBeTrue()
    }

    @Test
    fun `exportBitmap DOWNLOADS on API 36 hits external storage path without crashing`() {
        ctx.exportBitmap(SaveLocation.DOWNLOADS, bitmap, "test", null)
    }

    @Test
    fun `toLocalizedString returns non-blank string for CUSTOM`() {
        SaveLocation.CUSTOM.toLocalizedString(ctx).shouldNotBeBlank()
    }

    @Test
    fun `toLocalizedString returns non-blank string for DOWNLOADS`() {
        SaveLocation.DOWNLOADS.toLocalizedString(ctx).shouldNotBeBlank()
    }

    @Test
    fun `toLocalizedString returns non-blank string for PICTURES`() {
        SaveLocation.PICTURES.toLocalizedString(ctx).shouldNotBeBlank()
    }

    @Test
    fun `toLocalizedString returns non-blank string for DCIM`() {
        SaveLocation.DCIM.toLocalizedString(ctx).shouldNotBeBlank()
    }

    @Test
    fun `getLocalizedEntries returns four entries`() {
        val entries = SaveLocation.getLocalizedEntries(ctx)
        entries.size shouldBe 4
    }

    @Test
    fun `saveBitmapToUri success returns true when stream opens and compress succeeds`() {
        val file = File(ctx.cacheDir, "export_test.png").also { it.createNewFile() }
        val uri = Uri.fromFile(file)
        ctx.saveBitmapToUri(uri, bitmap).shouldBeTrue()
    }

    @Test
    fun `saveBitmapToUri compress fail returns false`() {
        val file = File(ctx.cacheDir, "export_fail.png").also { it.createNewFile() }
        val uri = Uri.fromFile(file)
        val failBitmap = mockk<Bitmap>()
        every { failBitmap.compress(any(), any(), any<OutputStream>()) } returns false
        ctx.saveBitmapToUri(uri, failBitmap).shouldBeFalse()
    }

    @Test
    fun `saveBitmapToUri exception returns false`() {
        // Pass a content URI with no registered provider → openOutputStream throws
        val uri = Uri.parse("content://de.lemke.nonexistent/data/1")
        ctx.saveBitmapToUri(uri, bitmap).shouldBeFalse()
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
    fun `exportBitmap to DOWNLOADS shows the error toast and returns false when the output stream cannot open`() {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloads.deleteRecursively()
        downloads.parentFile!!.mkdirs()
        downloads.createNewFile().shouldBeTrue()
        ctx.exportBitmap(SaveLocation.DOWNLOADS, bitmap, "test", null).shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
    }

    @Test
    fun `saveBitmapToUri null stream shows the error toast and returns false`() {
        Robolectric.buildContentProvider(NoFileContentProvider::class.java).create("de.lemke.nofile")
        ctx.saveBitmapToUri(Uri.parse("content://de.lemke.nofile/1"), bitmap).shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
    }

    @Test
    fun `exportBitmap PICTURES on API 36 directory success returns true`() {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        dir.mkdirs()
        ctx.exportBitmap(SaveLocation.PICTURES, bitmap, "test", null).shouldBeTrue()
    }

    @Test
    fun `exportBitmap DCIM on API 36 directory compress-fail returns false`() {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        dir.mkdirs()
        val failBitmap = mockk<Bitmap>()
        every { failBitmap.compress(any(), any(), any<OutputStream>()) } returns false
        ctx.exportBitmap(SaveLocation.DCIM, failBitmap, "test", null).shouldBeFalse()
    }

    @Test
    fun `exportBitmap DCIM on API 36 directory success returns true`() {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        dir.mkdirs()
        ctx.exportBitmap(SaveLocation.DCIM, bitmap, "test", null).shouldBeTrue()
    }

    @Test
    fun `exportBitmap DOWNLOADS on API 36 directory success returns true`() {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        ctx.exportBitmap(SaveLocation.DOWNLOADS, bitmap, "test", null).shouldBeTrue()
    }

    @Test
    fun `exportBitmap launcher throws ActivityNotFoundException returns false`() {
        val launcher = mockk<ActivityResultLauncher<Intent>>()
        every { launcher.launch(any()) } throws ActivityNotFoundException("no picker")
        ctx.exportBitmap(SaveLocation.CUSTOM, bitmap, "test", launcher).shouldBeFalse()
    }

    @Test
    fun `saveBitmapToUri non-null uri and null bitmap returns false`() {
        // uri != null → check second: bitmap == null → true → if-body → false (covers || right-side branch)
        val uri = Uri.fromFile(File(ctx.cacheDir, "null_bitmap_test.png"))
        ctx.saveBitmapToUri(uri, null).shouldBeFalse()
    }

    @Test
    fun `exportBitmap IOException from compress covers catch block`() {
        // Make Bitmap.compress throw IOException inside the try block → caught → returns false
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        dir.mkdirs()
        val throwingBitmap = mockk<Bitmap>()
        every { throwingBitmap.compress(any(), any(), any<OutputStream>()) } throws java.io.IOException("simulated IO error")
        ctx.exportBitmap(SaveLocation.PICTURES, throwingBitmap, "test", null).shouldBeFalse()
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
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val bitmap: Bitmap get() = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    @Test
    fun `exportBitmap DOWNLOADS on API 29 SDK not gt Q falls through to else-if branch`() {
        // SDK_INT (29) > Q (29) = false → else-if: activityResultLauncher == null → toast + false
        ctx.exportBitmap(SaveLocation.DOWNLOADS, bitmap, "test", null).shouldBeFalse()
    }
}
