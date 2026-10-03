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
import android.graphics.Bitmap
import android.net.Uri
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
import de.lemke.commonutils.ui.utils.createBitmapShareUri
import de.lemke.commonutils.ui.utils.onSingleLaunchClick
import de.lemke.commonutils.ui.utils.quickShareBitmap
import de.lemke.commonutils.ui.utils.shareBitmap
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
import java.io.File
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
class LaunchLatchLeavingRobolectricTest : LaunchLatchRobolectricTest() {
    @Test
    fun `leaving input drops`() {
        val activity = resumed().get()
        activity.launchScreen()
        var runs = 0

        activity.singleLaunch { runs++ }.shouldBeFalse()

        runs shouldBe 0
    }

    @Test
    fun `leaving suspend start drops`() {
        val activity = resumed().get()
        activity.launchScreen()
        var worked = false

        activity.singleLaunchSuspending(work = { worked = true }, then = {}).shouldBeFalse()
        shadowLooper.idle()

        worked.shouldBeFalse()
    }

    @Test
    fun `leaving suspend end releases busy and stays leaving`() {
        val activity = resumed().get()
        val work = CompletableDeferred<Unit>()
        var thenLaunched: Boolean? = null
        activity.singleLaunchSuspending(work = { work.await() }, then = { thenLaunched = activity.launchScreen() })
        activity.launchScreen().shouldBeTrue()

        work.complete(Unit)
        shadowLooper.idle()

        thenLaunched shouldBe false
        activity.singleLaunch {}.shouldBeFalse()
        shadowLooper.idleFor(Duration.ofSeconds(1))
        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `leaving unmarked launch drops`() {
        val activity = resumed().get()
        activity.launchScreen()

        activity.launchScreen().shouldBeFalse()

        shadowOf(activity).nextStartedActivity.shouldNotBeNull()
        shadowOf(activity).nextStartedActivity.shouldBeNull()
    }

    @Test
    fun `leaving marked launch drops`() {
        val activity = resumed().get()
        val request = activity.launchForResult()
        shadowLooper.idleFor(Duration.ofSeconds(1))
        activity.launchScreen()
        var launched: Boolean? = null
        activity.onResult = { launched = activity.launchScreen() }

        activity.deliverResult(request)

        launched shouldBe false
    }

    @Test
    fun `leaving launch that throws drops without running`() {
        val activity = resumed().get()
        activity.launchScreen()
        val failing = StartActivityFailingContext(activity, ActivityNotFoundException("no app"))

        failing.launchScreen().shouldBeFalse()

        failing.startedIntents.shouldBeEmpty()
        activity.singleLaunch {}.shouldBeFalse()
    }

    @Test
    fun `leaving owner result settles idle and runs the callback`() {
        val activity = resumed().get()
        val results = mutableListOf<Int>()
        activity.onResult = { results += it.resultCode }
        val request = activity.launchForResult()
        shadowLooper.idleFor(Duration.ofMillis(500))

        activity.deliverResult(request)

        results shouldContainExactly listOf(RESULT_OK)
        activity.singleLaunch {}.shouldBeTrue()
        activity.shouldHoldANewLaunchForOneSecond()
    }

    @Test
    fun `leaving foreign result runs the callback and stays leaving`() {
        val activity = resumed().get()
        activity.otherLauncher.launch(Intent(activity, Activity::class.java))
        val otherRequest = shadowOf(activity).nextStartedActivityForResult
        shadowLooper.idleFor(Duration.ofSeconds(1))
        activity.launchForResult()
        val results = mutableListOf<Int>()
        activity.onOtherResult = { results += it.resultCode }

        activity.deliverResult(otherRequest)

        results shouldContainExactly listOf(RESULT_OK)
        activity.singleLaunch {}.shouldBeFalse()
    }

    @Test
    fun `leaving new intent settles idle and cancels the timer`() {
        val activity = resumed().get()
        activity.launchScreen()
        shadowLooper.idleFor(Duration.ofMillis(500))

        activity.deliverNewIntent(Intent())

        activity.singleLaunch {}.shouldBeTrue()
        activity.shouldHoldANewLaunchForOneSecond()
    }

    @Test
    fun `leaving pause moves away and cancels the timer`() {
        val controller = resumed()
        controller.get().launchScreen()

        controller.pause()

        shadowLooper.idleFor(Duration.ofSeconds(2))
        controller.get().launchScreen().shouldBeFalse()
    }

    @Test
    fun `leaving pre-resume stays leaving and arms the timer`() {
        val controller = resumed()
        controller.pause()
        controller.get().launchScreen().shouldBeTrue()
        shadowLooper.idleFor(Duration.ofSeconds(2))

        controller.resume()

        controller.get().singleLaunch {}.shouldBeFalse()
        shadowLooper.idleFor(Duration.ofSeconds(1))
        controller.get().singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `leaving timer settles idle`() {
        val activity = resumed().get()
        activity.launchScreen()

        shadowLooper.idleFor(Duration.ofSeconds(1))

        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `leaving destroy removes the latch`() {
        val controller = untrackedResumed()
        val twin = untrackedResumed()
        controller.pause().stop()
        controller.get().launchScreen().shouldBeTrue()

        controller.destroy()
        twin.pause().stop().destroy()

        controller.observerCount shouldBe twin.observerCount
        controller.get().launchScreen().shouldBeTrue()
    }

    @Test
    fun `leaving busy pre-resume stays leaving and keeps busy`() {
        val controller = resumed()
        val activity = controller.get()
        val work = activity.startBusy()
        controller.pause()
        activity.launchScreen().shouldBeTrue()

        controller.resume()

        activity.launchScreen().shouldBeFalse()
        shadowLooper.idleFor(Duration.ofSeconds(1))
        activity.singleLaunch {}.shouldBeFalse()
        work.complete(Unit)
        shadowLooper.idle()
        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    @Config(shadows = [ShadowFileProvider::class])
    fun `leaving bitmap share input drops before its write and keeps the pending share's file`() {
        val activity = resumed().get()
        activity.launchScreen()
        val pending =
            File(activity.cacheDir, "share/shared.png").apply {
                parentFile?.mkdirs()
                writeText("pending")
            }

        activity
            .singleLaunchSuspending(
                work = { activity.createBitmapShareUri(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "shared.png") },
                then = { activity.shareBitmap(it) },
            ).shouldBeFalse()
        shadowLooper.idle()

        pending.readText() shouldBe "pending"
    }

    @Test
    fun `leaving bitmap share and quick share drop`() {
        val activity = resumed().get()
        activity.launchScreen()
        shadowOf(activity).clearNextStartedActivities()
        val uri = Uri.parse("content://de.lemke.commonutils.test.fileprovider/cache_root/share/shared.png")

        activity.shareBitmap(uri).shouldBeFalse()
        activity.quickShareBitmap(uri).shouldBeFalse()

        shadowOf(activity).nextStartedActivity.shouldBeNull()
    }
}
