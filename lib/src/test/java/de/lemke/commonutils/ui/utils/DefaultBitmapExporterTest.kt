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

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.HeldDispatcher
import de.lemke.commonutils.RecordingDocumentsProvider
import de.lemke.commonutils.ShadowFileProvider
import de.lemke.commonutils.data.SaveLocation
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CLIP_LABEL = "QR Code"
private const val CACHE_FILE_NAME = "QRCode.png"

private val timestampedPng = Regex("""QRCode_\d{4}_\d{2}_\d{2}_\d{2}_\d{2}_\d{2}\.png""")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowFileProvider::class])
class DefaultBitmapExporterTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun exporter(io: CoroutineDispatcher) = DefaultBitmapExporter(ctx, io, CLIP_LABEL, CACHE_FILE_NAME)

    private fun TestScope.exporter() = exporter(UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.shouldRunOnIo(call: suspend BitmapExporter.() -> Any?) {
        val io = HeldDispatcher()
        val result = async { exporter(io).call() }
        runCurrent()

        result.isCompleted.shouldBeFalse()

        io.held.removeFirst().run()
        runCurrent()
        result.isCompleted.shouldBeTrue()
    }

    // ── saveToDirectory ─────────────────────────────────────────────────────────

    @Test
    fun `saveToDirectory writes a timestamped PNG to Pictures`() =
        runTest {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).apply { mkdirs() }

            exporter().saveToDirectory(SaveLocation.PICTURES, pngBitmap(), "QRCode") shouldBe BitmapSaveResult.Saved(SaveLocation.PICTURES)

            directory.listFiles()!!.single().name shouldMatch timestampedPng
        }

    @Test
    fun `saveToDirectory returns NeedsPicker for a location that needs the picker`() =
        runTest {
            exporter().saveToDirectory(SaveLocation.CUSTOM, pngBitmap(), "QRCode") shouldBe BitmapSaveResult.NeedsPicker
        }

    @Test
    fun `saveToDirectory writes on the IO dispatcher`() =
        runTest {
            shouldRunOnIo { saveToDirectory(SaveLocation.PICTURES, pngBitmap(), "QRCode") }
        }

    // ── saveToCreatedDocument ───────────────────────────────────────────────────

    @Test
    fun `saveToCreatedDocument writes the PNG to the document and keeps it`() =
        runTest {
            val provider = RecordingDocumentsProvider.create(ctx)

            exporter().saveToCreatedDocument(provider.uri, pngBitmap()) shouldBe BitmapSaveResult.Saved(SaveLocation.CUSTOM)

            provider.file.readBytes().take(4) shouldContainExactly
                listOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
            provider.deleted.shouldBeEmpty()
        }

    @Test
    fun `saveToCreatedDocument with a null bitmap returns WriteFailed and deletes the created document`() =
        runTest {
            val provider = RecordingDocumentsProvider.create(ctx)

            exporter().saveToCreatedDocument(provider.uri, null) shouldBe BitmapSaveResult.WriteFailed

            provider.deleted shouldContainExactly listOf(provider.uri)
        }

    @Test
    fun `saveToCreatedDocument writes on the IO dispatcher`() =
        runTest {
            val provider = RecordingDocumentsProvider.create(ctx)

            shouldRunOnIo { saveToCreatedDocument(provider.uri, pngBitmap()) }
        }

    // ── createClip ──────────────────────────────────────────────────────────────

    @Test
    fun `createClip clips the PNG cache file under the clip label and cache file name`() =
        runTest {
            val clip = exporter().createClip(pngBitmap()).shouldNotBeNull()

            clip.description.label shouldBe CLIP_LABEL
            clip.description.getMimeType(0) shouldBe "image/png"
            clip.getItemAt(0).uri.lastPathSegment shouldBe CACHE_FILE_NAME
        }

    @Test
    fun `createClip writes on the IO dispatcher`() =
        runTest {
            shouldRunOnIo { createClip(pngBitmap()) }
        }

    // ── createShareFile ─────────────────────────────────────────────────────────

    @Test
    fun `createShareFile writes the PNG cache file under the cache file name`() =
        runTest {
            val file = exporter().createShareFile(pngBitmap()).shouldBeInstanceOf<BitmapShareFile.Written>()

            file.uri.lastPathSegment shouldBe CACHE_FILE_NAME
        }

    @Test
    fun `createShareFile writes on the IO dispatcher`() =
        runTest {
            shouldRunOnIo { createShareFile(pngBitmap()) }
        }
}
