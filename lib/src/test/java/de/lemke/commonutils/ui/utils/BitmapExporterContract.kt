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
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.RecordingDocumentsProvider
import de.lemke.commonutils.data.SaveLocation
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal fun pngBitmap(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

/** Behaviour every [BitmapExporter] promises, run against [DefaultBitmapExporter] and [FakeBitmapExporter]. */
abstract class BitmapExporterContract {
    protected val ctx: Context get() = ApplicationProvider.getApplicationContext()

    abstract fun exporter(): BitmapExporter

    /** A document that the `ACTION_CREATE_DOCUMENT` picker created. */
    abstract fun createdDocument(): Uri

    abstract fun deletedDocuments(): List<Uri>

    @Test
    fun `saveToDirectory to a location that needs the picker returns NeedsPicker`() =
        runTest {
            exporter().saveToDirectory(SaveLocation.CUSTOM, pngBitmap(), "test") shouldBe BitmapSaveResult.NeedsPicker
        }

    @Test
    fun `saveToDirectory to a public directory returns Saved to that location`() =
        runTest {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).mkdirs()

            exporter().saveToDirectory(SaveLocation.PICTURES, pngBitmap(), "test") shouldBe BitmapSaveResult.Saved(SaveLocation.PICTURES)
        }

    @Test
    fun `saveToCreatedDocument with a null bitmap returns WriteFailed and deletes the document`() =
        runTest {
            val document = createdDocument()

            exporter().saveToCreatedDocument(document, null) shouldBe BitmapSaveResult.WriteFailed

            deletedDocuments() shouldContainExactly listOf(document)
        }

    @Test
    fun `saveToCreatedDocument that writes the bitmap returns Saved to CUSTOM and keeps the document`() =
        runTest {
            exporter().saveToCreatedDocument(createdDocument(), pngBitmap()) shouldBe BitmapSaveResult.Saved(SaveLocation.CUSTOM)

            deletedDocuments().shouldBeEmpty()
        }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DefaultBitmapExporterContractTest : BitmapExporterContract() {
    private val provider by lazy { RecordingDocumentsProvider.create(ctx) }

    override fun exporter(): BitmapExporter = DefaultBitmapExporter(ctx, Dispatchers.Unconfined, "label", "test.png")

    override fun createdDocument(): Uri = provider.uri

    override fun deletedDocuments(): List<Uri> = provider.deleted
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FakeBitmapExporterContractTest : BitmapExporterContract() {
    private val fake = FakeBitmapExporter()

    override fun exporter(): BitmapExporter = fake

    override fun createdDocument(): Uri = Uri.parse("content://${RecordingDocumentsProvider.AUTHORITY}/document/1")

    override fun deletedDocuments(): List<Uri> = fake.deletedDocuments
}
