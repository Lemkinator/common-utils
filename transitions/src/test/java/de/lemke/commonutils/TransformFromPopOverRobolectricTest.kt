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

import android.animation.StateListAnimator
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import androidx.reflect.DeviceInfo
import androidx.reflect.SeslBaseReflector
import de.lemke.commonutils.ui.utils.popOverOrTransformToActivity
import de.lemke.commonutils.ui.utils.transformToActivity
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

private const val POP_OVER = "sw360dp-w360dp-h731dp"
private const val ORIGIN_VIEW_ID = 4243

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = POP_OVER)
class TransformFromPopOverRobolectricTest : LaunchLatchRobolectricTest() {
    private val semIsPopOver = Configuration::class.java.getMethod("toString")

    @After
    fun unmockPlatform() {
        unmockkStatic(DeviceInfo::class)
        unmockkStatic(SeslBaseReflector::class)
    }

    private fun platform(isPopOver: Boolean) {
        mockkStatic(DeviceInfo::class)
        every { DeviceInfo.isOneUI() } returns true
        mockkStatic(SeslBaseReflector::class)
        every { SeslBaseReflector.getDeclaredMethod(Configuration::class.java, "semIsPopOver") } returns semIsPopOver
        every { SeslBaseReflector.invoke(any(), semIsPopOver) } returns isPopOver
    }

    private fun popOverActivity(): LatchTestActivity {
        platform(isPopOver = true)
        return resumed().get()
    }

    private fun Activity.originView(): View = View(this).apply { id = ORIGIN_VIEW_ID }.also(::setContentView)

    private fun LatchTestActivity.startedLaunch(): IntentForResult = shadowOf(this).nextStartedActivityForResult.shouldNotBeNull()

    private fun IntentForResult.shouldBePopOver() = intent.shouldBePopOverTo(Activity::class.java, options)

    @Test
    fun `a view transform from a pop-over starts a pop-over page and leaves the view alone`() {
        val activity = popOverActivity()
        val view = activity.originView()
        val stateListAnimator = StateListAnimator()
        view.stateListAnimator = stateListAnimator

        view.transformToActivity(Intent(activity, Activity::class.java), "shared").shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
        view.transitionName.shouldBeNull()
        view.stateListAnimator shouldBe stateListAnimator
    }

    @Test
    fun `a view transform to a class from a pop-over starts a pop-over page`() {
        val activity = popOverActivity()

        activity.originView().transformToActivity(Activity::class.java).shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `a reified view transform from a pop-over starts a pop-over page`() {
        val activity = popOverActivity()

        activity.originView().transformToActivity<Activity>().shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `a detached view in a pop-over starts a pop-over page`() {
        val activity = popOverActivity()

        View(activity).transformToActivity(Intent(activity, Activity::class.java)).shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `an activity transform by view id from a pop-over starts a pop-over page`() {
        val activity = popOverActivity()
        activity.originView()

        activity.transformToActivity(ORIGIN_VIEW_ID, Activity::class.java).shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `an activity transform by view from a pop-over starts a pop-over page`() {
        val activity = popOverActivity()
        val view = activity.originView()

        activity.transformToActivity<Activity>(view).shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `a view pop-over-or-transform from a pop-over starts a pop-over page`() {
        val activity = popOverActivity()

        activity.originView().popOverOrTransformToActivity(Activity::class.java, "shared").shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `an activity pop-over-or-transform from a pop-over starts a pop-over page`() {
        val activity = popOverActivity()
        activity.originView()

        activity.popOverOrTransformToActivity<Activity>(ORIGIN_VIEW_ID, "shared").shouldBeTrue()

        activity.startedLaunch().shouldBePopOver()
    }

    @Test
    fun `a second launch from a pop-over is dropped`() {
        val activity = popOverActivity()
        val view = activity.originView()

        view.transformToActivity(Activity::class.java).shouldBeTrue()
        view.transformToActivity(Activity::class.java).shouldBeFalse()

        activity.startedLaunch().shouldBePopOver()
        shadowOf(activity).nextStartedActivityForResult.shouldBeNull()
    }

    @Test
    fun `a view transform outside a pop-over keeps the container transform`() {
        platform(isPopOver = false)
        val activity = resumed().get()

        activity.originView().transformToActivity(Activity::class.java, "shared").shouldBeTrue()

        val started = activity.startedLaunch()
        started.intent.shouldBeContainerTransformTo(Activity::class.java, started.options, "shared")
    }
}
