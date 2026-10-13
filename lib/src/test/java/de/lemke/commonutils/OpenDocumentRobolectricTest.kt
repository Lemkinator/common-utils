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
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.LaunchOutcome
import de.lemke.commonutils.ui.utils.SingleLaunchResultLauncher
import de.lemke.commonutils.ui.utils.launchOpenDocument
import de.lemke.commonutils.ui.utils.registerForOpenDocument
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

private val pickedUri = Uri.parse("content://de.lemke.commonutils.test.documents/document/1")

class OpenDocumentTestActivity : AppCompatActivity() {
    val picks = mutableListOf<Uri?>()
    val picker: SingleLaunchResultLauncher<Intent> = registerForOpenDocument { picks += it }
}

class OpenDocumentTestFragment : Fragment() {
    val picks = mutableListOf<Uri?>()
    val picker: SingleLaunchResultLauncher<Intent> = registerForOpenDocument { picks += it }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = View(inflater.context)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenDocumentRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    @get:Rule
    val drainMainLooper = DrainMainLooperRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun activity(): OpenDocumentTestActivity =
        Robolectric
            .buildActivity(OpenDocumentTestActivity::class.java)
            .setup()
            .track(destroyActivities)
            .get()

    private fun OpenDocumentTestActivity.addFragment(): OpenDocumentTestFragment =
        OpenDocumentTestFragment().also { supportFragmentManager.beginTransaction().add(android.R.id.content, it).commitNow() }

    @Test
    fun `launchOpenDocument launches the open-document picker for the given type`() {
        val launcher = RecordingIntentLauncher()

        ctx.launchOpenDocument("application/json", launcher) shouldBe LaunchOutcome.Started

        val intent = launcher.launched.single()
        intent.action shouldBe Intent.ACTION_OPEN_DOCUMENT
        intent.categories shouldContainExactly setOf(Intent.CATEGORY_OPENABLE)
        intent.type shouldBe "application/json"
    }

    @Test
    fun `launchOpenDocument whose launch the latch drops returns Dropped without a toast`() {
        val launcher = RecordingIntentLauncher(admits = false)

        ctx.launchOpenDocument("application/json", launcher) shouldBe LaunchOutcome.Dropped

        launcher.launched.shouldBeEmpty()
        ShadowToast.getLatestToast().shouldBeNull()
    }

    @Test
    fun `launchOpenDocument without a document picker shows the not-supported toast once and returns Failed`() {
        val launcher = RecordingIntentLauncher(ActivityNotFoundException("no picker"))

        ctx.launchOpenDocument("application/json", launcher) shouldBe LaunchOutcome.Failed

        ShadowToast.shownToastCount() shouldBe 1
        ShadowToast.getTextOfLatestToast() shouldBe "Error: Opening content is not supported on your device."
    }

    @Test
    fun `launchOpenDocument whose launch throws any exception shows the not-supported toast and returns Failed`() {
        val launcher = RecordingIntentLauncher(SecurityException("provider denied"))

        ctx.launchOpenDocument("application/json", launcher) shouldBe LaunchOutcome.Failed

        ShadowToast.getTextOfLatestToast() shouldBe "Error: Opening content is not supported on your device."
    }

    @Test
    fun `Fragment launchOpenDocument delegates to the context`() {
        val fragment = activity().addFragment()
        val launcher = RecordingIntentLauncher()

        fragment.launchOpenDocument("image/png", launcher) shouldBe LaunchOutcome.Started

        launcher.launched.single().type shouldBe "image/png"
    }

    @Test
    fun `activity launcher reports the picked document URI`() {
        val activity = activity()
        activity.picker.tryLaunch(Intent(Intent.ACTION_OPEN_DOCUMENT))

        activity.receivePickerResult(RESULT_OK, Intent().setData(pickedUri))

        activity.picks shouldContainExactly listOf(pickedUri)
    }

    @Test
    fun `activity launcher reports a canceled picker as null`() {
        val activity = activity()
        activity.picker.tryLaunch(Intent(Intent.ACTION_OPEN_DOCUMENT))

        activity.receivePickerResult(RESULT_CANCELED, Intent().setData(pickedUri))

        activity.picks shouldContainExactly listOf(null)
    }

    @Test
    fun `activity launcher reports a result without a URI as null`() {
        val activity = activity()
        activity.picker.tryLaunch(Intent(Intent.ACTION_OPEN_DOCUMENT))

        activity.receivePickerResult(RESULT_OK, null)

        activity.picks shouldContainExactly listOf(null)
    }

    @Test
    fun `activity launcher reports a result whose intent carries no URI as null`() {
        val activity = activity()
        activity.picker.tryLaunch(Intent(Intent.ACTION_OPEN_DOCUMENT))

        activity.receivePickerResult(RESULT_OK, Intent())

        activity.picks shouldContainExactly listOf(null)
    }

    @Test
    fun `fragment launcher reports the picked document URI`() {
        val activity = activity()
        val fragment = activity.addFragment()
        fragment.picker.tryLaunch(Intent(Intent.ACTION_OPEN_DOCUMENT))

        activity.receivePickerResult(RESULT_OK, Intent().setData(pickedUri))

        fragment.picks shouldContainExactly listOf(pickedUri)
    }
}
