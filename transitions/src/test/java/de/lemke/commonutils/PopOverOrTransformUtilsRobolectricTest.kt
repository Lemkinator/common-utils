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
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.reflect.DeviceInfo
import de.lemke.commonutils.ui.utils.TRANSITION_NAME_KEY
import de.lemke.commonutils.ui.utils.popOverOrTransformToActivity
import de.lemke.commonutils.ui.utils.singleLaunch
import dev.oneuiproject.oneui.popover.PopOverOptions
import io.kotest.assertions.throwables.shouldThrow
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity.IntentForResult

private const val TABLET = "sw600dp-w600dp-h1024dp"
private const val LARGE_PHONE = "sw599dp-w599dp-h1024dp"
private const val ANCHOR_VIEW_ID = 4242
private const val NARROW_SMALLEST_WIDTH_DP = 500

/** An activity whose own resources report a phone width while the application stays at a tablet width. */
class NarrowActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        applyOverrideConfiguration(Configuration().apply { smallestScreenWidthDp = NARROW_SMALLEST_WIDTH_DP })
        super.attachBaseContext(newBase)
    }
}

class FailingStartActivity : AppCompatActivity() {
    override fun startActivity(intent: Intent?) = throw ActivityNotFoundException("no app")

    override fun startActivity(
        intent: Intent?,
        options: Bundle?,
    ) = throw ActivityNotFoundException("no app")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = TABLET)
class PopOverOrTransformUtilsRobolectricTest : LaunchLatchRobolectricTest() {
    @After
    fun unmockDeviceInfo() {
        unmockkStatic(DeviceInfo::class)
    }

    private fun oneUi(isOneUi: Boolean) {
        mockkStatic(DeviceInfo::class)
        every { DeviceInfo.isOneUI() } returns isOneUi
    }

    private fun Activity.attachedView(): View = View(this).apply { id = ANCHOR_VIEW_ID }.also(::setContentView)

    private fun LatchTestActivity.startedLaunch(): IntentForResult = shadowOf(this).nextStartedActivityForResult.shouldNotBeNull()

    private fun IntentForResult.shouldBePopOver() = intent.shouldBePopOverTo(Activity::class.java, options)

    private fun IntentForResult.shouldBeZoom(transitionName: String) =
        intent.shouldBeContainerTransformTo(Activity::class.java, options, transitionName)

    @Test
    fun `class overload pops over on One UI at 600dp smallest width`() {
        oneUi(true)
        val activity = resumed().get()

        activity.attachedView().popOverOrTransformToActivity(Activity::class.java).shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `intent overload pops over with the given options and keeps the caller intent unchanged`() {
        oneUi(true)
        val activity = resumed().get()
        val intent = Intent(activity, Activity::class.java)

        activity
            .attachedView()
            .popOverOrTransformToActivity(intent, "shared", PopOverOptions.topCenterAnchored(activity))
            .shouldBeTrue()

        intent.flags shouldBe 0
        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `reified overload pops over on One UI at 600dp smallest width`() {
        oneUi(true)
        val activity = resumed().get()

        activity.attachedView().popOverOrTransformToActivity<Activity>().shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    @Config(qualifiers = LARGE_PHONE)
    fun `a One UI device below 600dp smallest width zooms`() {
        oneUi(true)
        val activity = resumed().get()

        activity.attachedView().popOverOrTransformToActivity(Activity::class.java, "shared").shouldBeTrue()

        activity.startedLaunch().shouldBeZoom("shared")
    }

    @Test
    fun `a large screen without One UI zooms`() {
        oneUi(false)
        val activity = resumed().get()

        activity.attachedView().popOverOrTransformToActivity(Activity::class.java, "shared").shouldBeTrue()

        activity.startedLaunch().shouldBeZoom("shared")
    }

    @Test
    fun `the default transition name applies to the zoom`() {
        oneUi(false)
        val activity = resumed().get()

        activity.attachedView().popOverOrTransformToActivity<Activity>().shouldBeTrue()

        activity.startedLaunch().shouldBeZoom("commonUtilsActivityTransitionName")
    }

    @Test
    fun `a view outside an activity starts the page without a transition`() {
        oneUi(true)
        val activity = resumed().get()

        val intent = Intent(activity.applicationContext, Activity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        View(activity.applicationContext).popOverOrTransformToActivity(intent).shouldBeTrue()

        val started = shadowOf(activity.application).nextStartedActivity.shouldNotBeNull()
        started.component?.className shouldBe Activity::class.java.name
        started.hasExtra(TRANSITION_NAME_KEY).shouldBeFalse()
    }

    @Test
    fun `a repeat tap during a pending pop-over launch is dropped`() {
        oneUi(true)
        val activity = resumed().get()
        val view = activity.attachedView()

        view.popOverOrTransformToActivity(Activity::class.java).shouldBeTrue()
        view.popOverOrTransformToActivity(Activity::class.java).shouldBeFalse()

        shadowOf(activity).nextStartedActivityForResult.shouldNotBeNull()
        shadowOf(activity).nextStartedActivityForResult.shouldBeNull()
    }

    @Test
    fun `a repeat tap during a pending zoom launch is dropped`() {
        oneUi(false)
        val activity = resumed().get()
        val view = activity.attachedView()

        view.popOverOrTransformToActivity(Activity::class.java).shouldBeTrue()
        view.popOverOrTransformToActivity(Activity::class.java).shouldBeFalse()

        shadowOf(activity).nextStartedActivityForResult.shouldNotBeNull()
        shadowOf(activity).nextStartedActivityForResult.shouldBeNull()
    }

    @Test
    fun `the zoom arm carries the duration and fade mode`() {
        oneUi(false)
        val activity = resumed().get()

        activity.attachedView().popOverOrTransformToActivity(Activity::class.java, "shared", duration = 123L, fadeMode = 2).shouldBeTrue()

        val started = activity.startedLaunch()
        started.intent.shouldBeContainerTransformTo(Activity::class.java, started.options, "shared", 123L, 2)
    }

    @Test
    fun `a view in a themed wrapper of the activity resolves the activity`() {
        oneUi(true)
        val activity = resumed().get()
        activity.attachedView()
        val view = View(ContextThemeWrapper(activity, android.R.style.Theme))

        view.popOverOrTransformToActivity(Activity::class.java).shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `a throwing pop-over launch reopens the latch`() {
        oneUi(true)
        val activity =
            Robolectric
                .buildActivity(FailingStartActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        val view = activity.attachedView()

        shouldThrow<ActivityNotFoundException> { view.popOverOrTransformToActivity(Activity::class.java) }

        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `the condition reads the resources of the launching activity`() {
        oneUi(true)
        val activity =
            Robolectric
                .buildActivity(NarrowActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        activity.resources.configuration.smallestScreenWidthDp shouldBe NARROW_SMALLEST_WIDTH_DP
        activity.applicationContext.resources.configuration.smallestScreenWidthDp shouldBe 600

        activity.attachedView().popOverOrTransformToActivity(Activity::class.java, "shared").shouldBeTrue()

        shadowOf(activity).nextStartedActivityForResult.shouldNotBeNull().shouldBeZoom("shared")
    }

    @Test
    fun `the activity class overload pops over on One UI at 600dp smallest width`() {
        oneUi(true)
        val activity = resumed().get()
        activity.attachedView()

        activity.popOverOrTransformToActivity(ANCHOR_VIEW_ID, Activity::class.java).shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `the activity intent overload pops over without a view and keeps the caller intent unchanged`() {
        oneUi(true)
        val activity = resumed().get()
        val intent = Intent(activity, Activity::class.java)

        activity.popOverOrTransformToActivity(ANCHOR_VIEW_ID, intent).shouldBeTrue()

        intent.flags shouldBe 0
        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `the activity reified overload pops over on One UI at 600dp smallest width`() {
        oneUi(true)
        val activity = resumed().get()
        activity.attachedView()

        activity.popOverOrTransformToActivity<Activity>(ANCHOR_VIEW_ID).shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `the activity overload zooms from the view without One UI`() {
        oneUi(false)
        val activity = resumed().get()
        activity.attachedView()

        activity.popOverOrTransformToActivity(ANCHOR_VIEW_ID, Activity::class.java, "shared", duration = 123L, fadeMode = 2).shouldBeTrue()

        val started = activity.startedLaunch()
        started.intent.shouldBeContainerTransformTo(Activity::class.java, started.options, "shared", 123L, 2)
    }

    @Test
    fun `the activity overload without One UI and without the view starts without a transition`() {
        oneUi(false)
        val activity = resumed().get()

        activity.popOverOrTransformToActivity(ANCHOR_VIEW_ID, Activity::class.java, "shared").shouldBeTrue()

        val started = activity.startedLaunch()
        started.intent.component?.className shouldBe Activity::class.java.name
        started.intent.hasExtra(TRANSITION_NAME_KEY).shouldBeFalse()
    }

    @Test
    @Config(qualifiers = LARGE_PHONE)
    fun `the activity overload zooms below 600dp smallest width`() {
        oneUi(true)
        val activity = resumed().get()
        activity.attachedView()

        activity.popOverOrTransformToActivity(ANCHOR_VIEW_ID, Activity::class.java, "shared").shouldBeTrue()

        activity.startedLaunch().shouldBeZoom("shared")
    }
}
