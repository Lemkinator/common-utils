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

import android.Manifest.permission.CAMERA
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import de.lemke.commonutils.ui.utils.onSingleLaunchClick
import de.lemke.commonutils.ui.utils.singleLaunch
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.commonutils.ui.utils.singleLaunchMenuItem
import de.lemke.commonutils.ui.utils.singleLaunchSuspending
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity.IntentForResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaunchLatchIdleRobolectricTest : LaunchLatchRobolectricTest() {
    @Test
    fun `idle input runs while resumed`() {
        val activity = resumed().get()
        var runs = 0

        activity.singleLaunch { runs++ }.shouldBeTrue()

        runs shouldBe 1
    }

    @Test
    fun `idle input drops while not resumed`() {
        val controller = resumed()
        controller.pause()
        var runs = 0

        controller.get().singleLaunch { runs++ }.shouldBeFalse()

        runs shouldBe 0
    }

    @Test
    fun `idle input that launches nothing leaves the latch idle`() {
        val activity = resumed().get()

        activity.singleLaunch {}.shouldBeTrue()

        activity.singleLaunch {}.shouldBeTrue()
        activity.launchScreen().shouldBeTrue()
    }

    @Test
    fun `idle suspend start runs and drops inputs until it ends`() {
        val activity = resumed().get()
        val work = CompletableDeferred<String>()
        val results = mutableListOf<String>()

        activity.singleLaunchSuspending(work = { work.await() }, then = { results += it }).shouldBeTrue()

        activity.singleLaunch {}.shouldBeFalse()
        results.shouldBeEmpty()
    }

    @Test
    fun `idle suspend end runs then and releases busy`() {
        val activity = resumed().get()
        val work = CompletableDeferred<String>()
        val results = mutableListOf<String>()
        activity.singleLaunchSuspending(work = { work.await() }, then = { results += it })

        work.complete("generated")
        shadowLooper.idle()

        results shouldContainExactly listOf("generated")
        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `idle unmarked launch runs and leaves until the timer fires`() {
        val activity = resumed().get()

        activity.launchScreen().shouldBeTrue()

        activity.singleLaunch {}.shouldBeFalse()
        shadowLooper.idleFor(Duration.ofMillis(999))
        activity.singleLaunch {}.shouldBeFalse()
        shadowLooper.idleFor(Duration.ofMillis(1))
        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `idle marked launch runs and leaves`() {
        val activity = resumed().get()
        val request = activity.launchForResult()
        shadowLooper.idleFor(Duration.ofSeconds(1))
        var launched = false
        activity.onResult = { launched = activity.launchScreen() }

        activity.deliverResult(request)

        launched.shouldBeTrue()
        activity.singleLaunch {}.shouldBeFalse()
    }

    @Test
    fun `idle launch that throws stays idle`() {
        val activity = resumed().get()
        val failing = StartActivityFailingContext(activity, ActivityNotFoundException("no app"))

        shouldThrow<ActivityNotFoundException> { failing.launchScreen() }

        activity.singleLaunch {}.shouldBeTrue()
        shadowLooper.idleFor(Duration.ofMillis(500))
        activity.shouldHoldANewLaunchForOneSecond()
    }

    @Test
    fun `idle result runs the callback and stays idle`() {
        val activity = resumed().get()
        val request = activity.launchForResult()
        shadowLooper.idleFor(Duration.ofSeconds(1))
        val results = mutableListOf<Int>()
        activity.onResult = { results += it.resultCode }

        activity.deliverResult(request)

        results shouldContainExactly listOf(RESULT_OK)
        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `idle new intent stays idle`() {
        val activity = resumed().get()
        activity.singleLaunch {}

        activity.deliverNewIntent(Intent())

        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `idle pause stays idle`() {
        val controller = resumed()
        controller.get().singleLaunch {}

        controller.pause()

        controller.get().launchScreen().shouldBeTrue()
    }

    @Test
    fun `idle pre-resume stays idle`() {
        val controller = resumed()
        controller.get().singleLaunch {}

        controller.pause().resume()

        controller.get().singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `idle latch first created in the onResume body launches and settles after the timer`() {
        val controller = started()
        val activity = controller.get()
        var launched: Boolean? = null
        activity.onResumeAction = { launched = activity.launchScreen() }

        controller.resume()

        launched shouldBe true
        activity.shouldSettleAfterOneSecond()
    }

    @Test
    fun `idle latch first created in Fragment onResume launches and settles after the timer`() {
        val controller = started()
        val activity = controller.get()
        val fragment = activity.addFragment()
        var launched: Boolean? = null
        fragment.onResumeAction = { launched = fragment.requireContext().launchScreen() }

        controller.resume()

        launched shouldBe true
        activity.shouldSettleAfterOneSecond()
    }

    @Test
    fun `idle destroy removes the latch`() {
        val controller = untrackedResumed()
        val twin = untrackedResumed()
        controller.get().singleLaunch {}
        controller.observerCount shouldBe twin.observerCount + 1

        controller.pause().stop().destroy()
        twin.pause().stop().destroy()

        controller.observerCount shouldBe twin.observerCount
        controller.get().launchScreen().shouldBeTrue()
        controller.get().launchScreen().shouldBeTrue()
    }

    @Test
    fun `idle busy input drops`() {
        val activity = resumed().get()
        activity.startBusy()

        activity.singleLaunch {}.shouldBeFalse()
    }

    @Test
    fun `idle busy suspend start drops`() {
        val activity = resumed().get()
        activity.startBusy()
        var worked = false

        activity.singleLaunchSuspending(work = { worked = true }, then = {}).shouldBeFalse()
        shadowLooper.idle()

        worked.shouldBeFalse()
    }

    @Test
    fun `idle busy unmarked launch runs and leaves`() {
        val activity = resumed().get()
        activity.startBusy()

        activity.launchScreen().shouldBeTrue()

        activity.launchScreen().shouldBeFalse()
    }

    @Test
    fun `idle busy marked launch runs and leaves`() {
        val activity = resumed().get()
        val request = activity.launchForResult()
        shadowLooper.idleFor(Duration.ofSeconds(1))
        activity.startBusy()
        val launches = mutableListOf<Boolean>()
        activity.onResult = {
            launches += activity.launchScreen()
            launches += activity.launchScreen()
        }

        activity.deliverResult(request)

        launches shouldContainExactly listOf(true, false)
    }
}
