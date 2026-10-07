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

import android.net.Uri
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.FakeBitmapExporter.Call
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FakeBitmapExporterTest {
    private val fake = FakeBitmapExporter()
    private val document: Uri = Uri.parse("content://de.lemke.commonutils.test.documents/document/1")

    @Test
    fun `records every call with its arguments in order`() =
        runTest {
            val bitmap = pngBitmap()

            fake.saveToDirectory(SaveLocation.PICTURES, bitmap, "QRCode")
            fake.saveToCreatedDocument(document, null)
            fake.createClip(bitmap)
            fake.createShareFile(bitmap)

            fake.calls shouldContainExactly
                listOf(
                    Call.SaveToDirectory(SaveLocation.PICTURES, bitmap, "QRCode"),
                    Call.SaveToCreatedDocument(document, null),
                    Call.CreateClip(bitmap),
                    Call.CreateShareFile(bitmap),
                )
        }

    @Test
    fun `a pending gate holds a recorded call until the gate completes`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val bitmap = pngBitmap()
            fake.gate = gate
            fake.shareFile = BitmapShareFile.Dropped

            val shareFile = async { fake.createShareFile(bitmap) }
            runCurrent()

            fake.calls shouldContainExactly listOf(Call.CreateShareFile(bitmap))
            shareFile.isCompleted.shouldBeFalse()

            gate.complete(Unit)
            shareFile.await() shouldBe BitmapShareFile.Dropped
        }

    @Test
    fun `saveToCreatedDocument deletes the document for a configured result that is no Saved`() =
        runTest {
            fake.documentResult = BitmapSaveResult.EncodingFailed

            fake.saveToCreatedDocument(document, pngBitmap()) shouldBe BitmapSaveResult.EncodingFailed

            fake.deletedDocuments shouldContainExactly listOf(document)
        }
}
