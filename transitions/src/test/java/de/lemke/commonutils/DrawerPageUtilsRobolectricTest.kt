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
import android.content.Intent
import android.view.View
import androidx.reflect.DeviceInfo
import de.lemke.commonutils.ui.utils.TRANSITION_NAME_KEY
import de.lemke.commonutils.ui.utils.launchDrawerPage
import dev.oneuiproject.oneui.popover.PopOverOptions
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity.IntentForResult

private const val TABLET = "sw600dp-w600dp-h1024dp"
private const val LARGE_PHONE = "sw599dp-w599dp-h1024dp"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = TABLET)
class DrawerPageUtilsRobolectricTest : LaunchLatchRobolectricTest() {
    @After
    fun unmockDeviceInfo() {
        unmockkStatic(DeviceInfo::class)
    }

    private fun oneUi(isOneUi: Boolean) {
        mockkStatic(DeviceInfo::class)
        every { DeviceInfo.isOneUI() } returns isOneUi
    }

    private fun LatchTestActivity.attachedView(): View = View(this).also(::setContentView)

    private fun LatchTestActivity.startedLaunch(): IntentForResult = shadowOf(this).nextStartedActivityForResult.shouldNotBeNull()

    private fun IntentForResult.shouldBePopOver() {
        intent.component?.className shouldBe Activity::class.java.name
        (intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP) shouldBe Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.hasExtra(TRANSITION_NAME_KEY).shouldBeFalse()
        options.shouldNotBeNull()
    }

    private fun IntentForResult.shouldBeZoom(transitionName: String) {
        intent.component?.className shouldBe Activity::class.java.name
        intent.getStringExtra(TRANSITION_NAME_KEY) shouldBe transitionName
        (intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP) shouldBe 0
        options.shouldNotBeNull()
    }

    @Test
    fun `class overload pops over on One UI at 600dp smallest width`() {
        oneUi(true)
        val activity = resumed().get()

        activity.attachedView().launchDrawerPage(Activity::class.java).shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `intent overload pops over with the given options and keeps the caller intent unchanged`() {
        oneUi(true)
        val activity = resumed().get()
        val intent = Intent(activity, Activity::class.java)

        activity
            .attachedView()
            .launchDrawerPage(intent, "shared", PopOverOptions.topCenterAnchored(activity))
            .shouldBeTrue()

        intent.flags shouldBe 0
        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `reified overload pops over on One UI at 600dp smallest width`() {
        oneUi(true)
        val activity = resumed().get()

        activity.attachedView().launchDrawerPage<Activity>().shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    @Config(qualifiers = LARGE_PHONE)
    fun `a One UI device below 600dp smallest width zooms`() {
        oneUi(true)
        val activity = resumed().get()

        activity.attachedView().launchDrawerPage(Activity::class.java, "shared").shouldBeTrue()

        activity.startedLaunch().shouldBeZoom("shared")
    }

    @Test
    fun `a large screen without One UI zooms`() {
        oneUi(false)
        val activity = resumed().get()

        activity.attachedView().launchDrawerPage(Activity::class.java, "shared").shouldBeTrue()

        activity.startedLaunch().shouldBeZoom("shared")
    }

    @Test
    fun `the default transition name applies to the zoom`() {
        oneUi(false)
        val activity = resumed().get()

        activity.attachedView().launchDrawerPage<Activity>().shouldBeTrue()

        activity.startedLaunch().shouldBeZoom("commonUtilsActivityTransitionName")
    }

    @Test
    fun `a view outside an activity starts the page without a transition`() {
        oneUi(true)
        val activity = resumed().get()

        val intent = Intent(activity.applicationContext, Activity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        View(activity.applicationContext).launchDrawerPage(intent).shouldBeTrue()

        val started = shadowOf(activity.application).nextStartedActivity.shouldNotBeNull()
        started.component?.className shouldBe Activity::class.java.name
        started.hasExtra(TRANSITION_NAME_KEY).shouldBeFalse()
    }

    @Test
    fun `a repeat tap during a pending pop-over launch is dropped`() {
        oneUi(true)
        val activity = resumed().get()
        val view = activity.attachedView()

        view.launchDrawerPage(Activity::class.java).shouldBeTrue()
        view.launchDrawerPage(Activity::class.java).shouldBeFalse()

        shadowOf(activity).nextStartedActivityForResult.shouldNotBeNull()
        shadowOf(activity).nextStartedActivityForResult.shouldBeNull()
    }

    @Test
    fun `a repeat tap during a pending zoom launch is dropped`() {
        oneUi(false)
        val activity = resumed().get()
        val view = activity.attachedView()

        view.launchDrawerPage(Activity::class.java).shouldBeTrue()
        view.launchDrawerPage(Activity::class.java).shouldBeFalse()

        shadowOf(activity).nextStartedActivityForResult.shouldNotBeNull()
        shadowOf(activity).nextStartedActivityForResult.shouldBeNull()
    }
}
