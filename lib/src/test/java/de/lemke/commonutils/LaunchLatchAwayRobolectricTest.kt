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
class LaunchLatchAwayRobolectricTest : LaunchLatchRobolectricTest() {
    @Test
    fun `away input drops`() {
        val controller = away()
        var runs = 0

        controller.get().singleLaunch { runs++ }.shouldBeFalse()

        runs shouldBe 0
    }

    @Test
    fun `away suspend start drops`() {
        val controller = away()
        var worked = false

        controller.get().singleLaunchSuspending(work = { worked = true }, then = {}).shouldBeFalse()
        shadowLooper.idle()

        worked.shouldBeFalse()
    }

    @Test
    fun `away suspend end releases busy and stays away`() {
        val controller = resumed()
        val activity = controller.get()
        val work = CompletableDeferred<Unit>()
        var thenRuns = 0
        activity.singleLaunchSuspending(work = { work.await() }, then = { thenRuns++ })
        activity.launchScreen()
        controller.pause()

        work.complete(Unit)
        shadowLooper.idle()

        thenRuns shouldBe 0
        activity.launchScreen().shouldBeFalse()
        controller.resume()
        thenRuns shouldBe 1
        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `away unmarked launch drops`() {
        val controller = away()

        controller.get().launchScreen().shouldBeFalse()
    }

    @Test
    fun `away marked launch runs and leaves`() {
        val controller = resumed()
        val activity = controller.get()
        val request = activity.launchForResult()
        controller.pause()
        val launches = mutableListOf<Boolean>()
        activity.onResult = {
            launches += activity.launchScreen()
            launches += activity.launchScreen()
        }

        activity.deliverResult(request)

        launches shouldContainExactly listOf(true, false)
    }

    @Test
    fun `away launch that throws stays away`() {
        val controller = resumed()
        val activity = controller.get()
        val request = activity.launchForResult()
        controller.pause()
        val failing = StartActivityFailingContext(activity, ActivityNotFoundException("no app"))
        activity.onResult = { shouldThrow<ActivityNotFoundException> { failing.launchScreen() } }

        activity.deliverResult(request)

        activity.launchScreen().shouldBeFalse()
        controller.resume()
        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `away result runs the callback and stays away`() {
        val controller = resumed()
        val activity = controller.get()
        val request = activity.launchForResult()
        controller.pause()
        val results = mutableListOf<Int>()
        activity.onResult = { results += it.resultCode }

        activity.deliverResult(request)

        results shouldContainExactly listOf(RESULT_OK)
        activity.launchScreen().shouldBeFalse()
    }

    @Test
    fun `away new intent marks a launch in its message`() {
        val controller = away()

        controller.get().deliverNewIntent(Intent())

        controller.get().launchScreen().shouldBeTrue()
        controller.get().launchScreen().shouldBeFalse()
    }

    @Test
    fun `away new intent marker ends with its message and stays away`() {
        val controller = away()

        controller.get().deliverNewIntent(Intent())
        shadowLooper.idle()

        controller.get().launchScreen().shouldBeFalse()
    }

    @Test
    fun `away pause stays away`() {
        val controller = away()

        controller.pause()

        controller.get().launchScreen().shouldBeFalse()
    }

    @Test
    fun `away pre-resume settles idle`() {
        val controller = away()

        controller.resume()

        controller.get().singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `away destroy removes the latch`() {
        val controller = untrackedResumed()
        val twin = untrackedResumed()
        controller.get().launchScreen()
        controller.pause()

        controller.stop().destroy()
        twin.pause().stop().destroy()

        controller.observerCount shouldBe twin.observerCount
        controller.get().launchScreen().shouldBeTrue()
    }

    @Test
    fun `away busy pre-resume settles idle and keeps busy`() {
        val controller = resumed()
        val activity = controller.get()
        val work = activity.startBusy()
        activity.launchScreen()
        controller.pause()

        controller.resume()

        activity.singleLaunch {}.shouldBeFalse()
        activity.launchScreen().shouldBeTrue()
        work.complete(Unit)
    }

    @Test
    fun `away resumed launch from the onResume body runs`() {
        val controller = away()
        var launched: Boolean? = null
        controller.get().onResumeAction = { launched = controller.get().launchScreen() }

        controller.resume()

        launched shouldBe true
    }

    @Test
    fun `away resumed launch from Fragment onResume runs`() {
        val controller = resumed()
        val fragment = controller.get().addFragment()
        controller.get().launchScreen()
        controller.pause()
        var launched: Boolean? = null
        fragment.onResumeAction = { launched = fragment.requireContext().launchScreen() }

        controller.resume()

        launched shouldBe true
    }

    @Test
    fun `away resumed launch from a resumed collector runs`() {
        val controller = resumed()
        val activity = controller.get()
        val events = Channel<Unit>(Channel.BUFFERED)
        val launches = mutableListOf<Boolean>()
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(RESUMED) { for (event in events) launches += activity.launchScreen() }
        }
        activity.launchScreen()
        controller.pause()

        events.trySend(Unit)
        shadowLooper.idle()
        launches.shouldBeEmpty()
        controller.resume()

        launches shouldContainExactly listOf(true)
    }
}
