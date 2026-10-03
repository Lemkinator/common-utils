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
class SingleLaunchUtilsRobolectricTest : LaunchLatchRobolectricTest() {
    // ── context ────────────────────────────────────────────────────────────────

    @Test
    fun `application context runs every call directly`() {
        val app = RuntimeEnvironment.getApplication()
        val intent = Intent(app, Activity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK)

        app.singleLaunchActivity(intent).shouldBeTrue()
        app.singleLaunchActivity(intent).shouldBeTrue()
        app.singleLaunch {}.shouldBeTrue()

        shadowOf(app).nextStartedActivity shouldBe intent
        shadowOf(app).nextStartedActivity shouldBe intent
    }

    @Test
    fun `destroyed activity runs every call directly without a latch`() {
        val controller = untrackedResumed()
        controller.get().singleLaunch {}
        controller.pause().stop().destroy()
        val countAfterDestroy = controller.observerCount
        resumed().get()

        controller.get().launchScreen().shouldBeTrue()
        controller.get().launchScreen().shouldBeTrue()
        controller.get().singleLaunchSuspending(work = {}, then = {}).shouldBeTrue()

        controller.observerCount shouldBe countAfterDestroy
    }

    @Test
    fun `detached fragment runs inputs directly`() {
        var runs = 0

        Fragment().singleLaunch { runs++ }.shouldBeTrue()
        Fragment().singleLaunch { runs++ }.shouldBeTrue()

        runs shouldBe 2
    }

    @Test
    fun `theme wrapper around the activity shares its latch`() {
        val activity = resumed().get()
        val wrapper = ContextThemeWrapper(activity, R.style.CommonUtils_AppTheme)

        wrapper.launchScreen().shouldBeTrue()

        activity.singleLaunch {}.shouldBeFalse()
        wrapper.singleLaunch {}.shouldBeFalse()
    }

    // ── end to end ─────────────────────────────────────────────────────────────

    @Test
    fun `granted permission result launches the next screen`() {
        val activity = resumed().get()
        shadowOf(activity.application).grantPermissions(CAMERA)
        var launched: Boolean? = null
        activity.onPermissionResult = { granted -> if (granted) launched = activity.launchScreen() }

        activity.permissionLauncher.launch(CAMERA)
        shadowLooper.idle()

        launched shouldBe true
        shadowOf(activity).nextStartedActivity.component?.className shouldBe Activity::class.java.name
    }

    @Test
    fun `fragment then waits on the activity not on its tab capped at started`() {
        val activity = resumed().get()
        val fragment = LatchTestFragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(android.R.id.content, fragment)
            .setMaxLifecycle(fragment, STARTED)
            .commitNow()
        val results = mutableListOf<String>()

        fragment.singleLaunchSuspending(work = { "level" }, then = { results += it }).shouldBeTrue()
        shadowLooper.idle()

        fragment.lifecycle.currentState shouldBe STARTED
        results shouldContainExactly listOf("level")
    }

    @Test
    fun `removed fragment cancels its suspend work and releases the latch`() {
        val activity = resumed().get()
        val fragment = activity.addFragment()
        val work = CompletableDeferred<Unit>()
        var thenRuns = 0
        fragment.singleLaunchSuspending(work = { work.await() }, then = { thenRuns++ }).shouldBeTrue()
        activity.singleLaunch {}.shouldBeFalse()

        activity.supportFragmentManager
            .beginTransaction()
            .remove(fragment)
            .commitNow()
        work.complete(Unit)
        shadowLooper.idle()

        thenRuns shouldBe 0
        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `recreation cancels suspend work and the new activity starts idle`() {
        val controller = resumed()
        val work = CompletableDeferred<Unit>()
        var thenRuns = 0
        controller.get().singleLaunchSuspending(work = { work.await() }, then = { thenRuns++ })
        controller.get().launchScreen()

        controller.recreate()

        work.complete(Unit)
        shadowLooper.idle()
        thenRuns shouldBe 0
        controller.get().singleLaunch {}.shouldBeTrue()
    }

    // ── API surface ────────────────────────────────────────────────────────────

    @Test
    fun `fragment input drops while its activity is leaving`() {
        val activity = resumed().get()
        val fragment = activity.addFragment()
        var runs = 0

        fragment.singleLaunch { runs++ }.shouldBeTrue()
        activity.launchScreen()
        fragment.singleLaunch { runs++ }.shouldBeFalse()

        runs shouldBe 1
    }

    @Test
    fun `fragment result launcher settles its own launch`() {
        val activity = resumed().get()
        val fragment = activity.addFragment()
        val results = mutableListOf<Int>()
        fragment.onResult = { results += it.resultCode }

        fragment.launcher.launch(Intent(activity, Activity::class.java))
        activity.singleLaunch {}.shouldBeFalse()
        activity.deliverResult(shadowOf(activity).nextStartedActivityForResult)

        results shouldContainExactly listOf(RESULT_OK)
        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `result launcher exposes its contract and unregisters`() {
        val activity = resumed().get()

        activity.launcher.contract.shouldBeInstanceOf<StartActivityForResult>()
        activity.launcher.unregister()

        shouldThrow<IllegalStateException> { activity.launchForResult() }
        activity.singleLaunch {}.shouldBeTrue()
    }

    @Test
    fun `single launch click drops a fast second click`() {
        val activity = resumed().get()
        val clicked = mutableListOf<View>()
        val view =
            View(activity).apply {
                onSingleLaunchClick {
                    clicked += it
                    activity.launchScreen()
                }
            }

        view.performClick()
        view.performClick()

        clicked shouldContainExactly listOf(view)
    }

    @Test
    fun `single launch preference click drops a fast second click and stays consumed`() {
        val activity = resumed().get()
        val clicked = mutableListOf<Preference>()
        val preference =
            Preference(activity).onSingleLaunchClick {
                clicked += it
                activity.launchScreen()
            }

        preference.onPreferenceClickListener?.onPreferenceClick(preference) shouldBe true
        preference.onPreferenceClickListener?.onPreferenceClick(preference) shouldBe true

        clicked shouldContainExactly listOf(preference)
    }

    @Test
    fun `single launch menu item drops a fast second selection and stays consumed`() {
        val activity = resumed().get()
        var runs = 0

        activity
            .singleLaunchMenuItem {
                runs++
                activity.launchScreen()
            }.shouldBeTrue()
        activity.singleLaunchMenuItem { runs++ }.shouldBeTrue()

        runs shouldBe 1
    }
}
