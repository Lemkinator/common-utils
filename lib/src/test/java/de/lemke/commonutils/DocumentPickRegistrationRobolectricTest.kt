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

import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import de.lemke.commonutils.ui.utils.DocumentPick
import de.lemke.commonutils.ui.utils.SingleLaunchResultLauncher
import de.lemke.commonutils.ui.utils.registerForDocumentPick
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private val pickedUri = Uri.parse("content://de.lemke.commonutils.test.documents/document/1")

/** Activity that records each [DocumentPick] of its [registerForDocumentPick] launcher. */
class DocumentPickTestActivity : AppCompatActivity() {
    val picks = mutableListOf<DocumentPick>()
    val picker: SingleLaunchResultLauncher<Intent> = registerForDocumentPick { picks += it }
}

/** Fragment that records each [DocumentPick] of its [registerForDocumentPick] launcher. */
class DocumentPickTestFragment : Fragment() {
    val picks = mutableListOf<DocumentPick>()
    val picker: SingleLaunchResultLauncher<Intent> = registerForDocumentPick { picks += it }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = View(inflater.context)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentPickRegistrationRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    @get:Rule
    val drainMainLooper = DrainMainLooperRule()

    private fun activity(): DocumentPickTestActivity =
        Robolectric
            .buildActivity(DocumentPickTestActivity::class.java)
            .setup()
            .track(destroyActivities)
            .get()

    private fun DocumentPickTestActivity.addFragment(): DocumentPickTestFragment =
        DocumentPickTestFragment().also { supportFragmentManager.beginTransaction().add(android.R.id.content, it).commitNow() }

    private fun Activity.deliverPickerResult(
        launcher: SingleLaunchResultLauncher<Intent>,
        resultCode: Int,
        data: Intent?,
    ) {
        launcher.tryLaunch(Intent(Intent.ACTION_CREATE_DOCUMENT)).shouldBeTrue()
        val request = shadowOf(this).nextStartedActivityForResult
        request.intent.action shouldBe Intent.ACTION_CREATE_DOCUMENT
        (this as AppCompatActivity).activityResultRegistry.dispatchResult(request.requestCode, resultCode, data).shouldBeTrue()
    }

    @Test
    fun `activity launcher reports a created document as Created`() {
        val activity = activity()

        activity.deliverPickerResult(activity.picker, RESULT_OK, Intent().setData(pickedUri))

        activity.picks shouldContainExactly listOf(DocumentPick.Created(pickedUri))
    }

    @Test
    fun `activity launcher reports a canceled picker as Canceled`() {
        val activity = activity()

        activity.deliverPickerResult(activity.picker, RESULT_CANCELED, null)

        activity.picks shouldContainExactly listOf(DocumentPick.Canceled)
    }

    @Test
    fun `activity launcher is gated by the launch latch`() {
        val activity = activity()

        activity.picker.tryLaunch(Intent(Intent.ACTION_CREATE_DOCUMENT)).shouldBeTrue()

        activity.picker.tryLaunch(Intent(Intent.ACTION_CREATE_DOCUMENT)).shouldBeFalse()
    }

    @Test
    fun `fragment launcher reports a result without a uri as MissingUri`() {
        val activity = activity()
        val fragment = activity.addFragment()

        activity.deliverPickerResult(fragment.picker, RESULT_OK, Intent())

        fragment.picks shouldContainExactly listOf(DocumentPick.MissingUri)
    }

    @Test
    fun `fragment launcher reports a created document as Created`() {
        val activity = activity()
        val fragment = activity.addFragment()

        activity.deliverPickerResult(fragment.picker, RESULT_OK, Intent().setData(pickedUri))

        fragment.picks shouldContainExactly listOf(DocumentPick.Created(pickedUri))
    }
}
