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

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import de.lemke.commonutils.ui.utils.DocumentPick
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportTestSupportRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    @get:Rule
    val drainMainLooper = DrainMainLooperRule()

    @get:Rule
    val tempFiles = TemporaryFolder()

    private fun activity(): DocumentPickTestActivity =
        Robolectric
            .buildActivity(DocumentPickTestActivity::class.java)
            .setup()
            .track(destroyActivities)
            .get()

    @Test
    fun `receivePickerResult delivers a picked document to the launcher that started the picker`() {
        val activity = activity()
        val uri = Uri.parse("content://de.lemke.commonutils.test.documents/document/1")
        activity.picker.tryLaunch(Intent(Intent.ACTION_CREATE_DOCUMENT)).shouldBeTrue()

        val pickerIntent = activity.receivePickerResult(RESULT_OK, Intent().setData(uri))

        pickerIntent.action shouldBe Intent.ACTION_CREATE_DOCUMENT
        activity.picks shouldContainExactly listOf(DocumentPick.Created(uri))
    }

    @Test
    fun `receivePickerResult delivers a canceled picker`() {
        val activity = activity()
        activity.picker.tryLaunch(Intent(Intent.ACTION_CREATE_DOCUMENT)).shouldBeTrue()

        activity.receivePickerResult(RESULT_CANCELED, null)

        activity.picks shouldContainExactly listOf(DocumentPick.Canceled)
    }

    @Test
    fun `hasPngSignature accepts the eight signature bytes`() {
        val png = tempFiles.newFile("signature.png")
        png.writeBytes(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 1))

        png.hasPngSignature().shouldBeTrue()
    }

    @Test
    fun `hasPngSignature rejects other content, a prefix and an empty file`() {
        val jpeg = tempFiles.newFile("other.png").apply { writeBytes(byteArrayOf(-1, -40, -1, -32, 0, 16, 74, 70)) }
        val prefix = tempFiles.newFile("prefix.png").apply { writeBytes(byteArrayOf(-119, 80, 78, 71)) }
        val empty: File = tempFiles.newFile("empty.png")

        listOf(jpeg, prefix, empty).map { it.hasPngSignature() } shouldContainExactly listOf(false, false, false)
    }
}
