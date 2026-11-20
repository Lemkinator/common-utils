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

import android.os.Build
import android.os.Looper
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import de.lemke.commonutils.ui.utils.addOnBackLogic
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Fake of the platform back dispatcher that records the registered callbacks. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class RecordingBackInvokedDispatcher : OnBackInvokedDispatcher {
    val callbacks = mutableListOf<OnBackInvokedCallback>()

    override fun registerOnBackInvokedCallback(
        priority: Int,
        callback: OnBackInvokedCallback,
    ) {
        callbacks += callback
    }

    override fun unregisterOnBackInvokedCallback(callback: OnBackInvokedCallback) {
        callbacks -= callback
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class RecordingBackActivity : AppCompatActivity() {
    val recordingDispatcher = RecordingBackInvokedDispatcher()

    override fun getOnBackInvokedDispatcher(): OnBackInvokedDispatcher = recordingDispatcher
}

@RunWith(RobolectricTestRunner::class)
class AddOnBackLogicRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun Fragment.addTo(activity: AppCompatActivity) =
        also {
            activity.supportFragmentManager
                .beginTransaction()
                .add(it, "host")
                .commitNow()
        }

    @Test
    @Config(sdk = [33])
    fun `on API 33 the logic registers a platform callback while enabled and runs on back`() {
        val activity =
            Robolectric
                .buildActivity(RecordingBackActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        val callbacks = activity.recordingDispatcher.callbacks
        val before = callbacks.size
        var backs = 0

        Fragment().addTo(activity).addOnBackLogic(MutableStateFlow(true)) { backs++ }
        idle()

        callbacks.size shouldBe before + 1
        callbacks.last().onBackInvoked()
        backs shouldBe 1
    }

    @Test
    @Config(sdk = [33])
    fun `on API 33 the platform callback follows the enabled flow`() {
        val activity =
            Robolectric
                .buildActivity(RecordingBackActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        val callbacks = activity.recordingDispatcher.callbacks
        val before = callbacks.size
        val enabled = MutableStateFlow(true)
        Fragment().addTo(activity).addOnBackLogic(enabled)
        idle()
        callbacks.size shouldBe before + 1

        enabled.value = false
        idle()
        callbacks.size shouldBe before

        enabled.value = true
        idle()
        callbacks.size shouldBe before + 1
    }

    @Test
    @Config(sdk = [33])
    fun `on API 33 removing the fragment unregisters the platform callback`() {
        val activity =
            Robolectric
                .buildActivity(RecordingBackActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        val callbacks = activity.recordingDispatcher.callbacks
        val before = callbacks.size
        val fragment = Fragment().addTo(activity)
        fragment.addOnBackLogic(MutableStateFlow(true))
        idle()
        callbacks.size shouldBe before + 1

        activity.supportFragmentManager
            .beginTransaction()
            .remove(fragment)
            .commitNow()
        idle()

        callbacks.size shouldBe before
    }

    @Test
    @Config(sdk = [32])
    fun `before API 33 the logic runs on back while enabled`() {
        val activity =
            Robolectric
                .buildActivity(AppCompatActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        var backs = 0
        Fragment().addTo(activity).addOnBackLogic(MutableStateFlow(true)) { backs++ }
        idle()

        activity.onBackPressedDispatcher.onBackPressed()

        backs shouldBe 1
        activity.isFinishing.shouldBeFalse()
    }

    @Test
    @Config(sdk = [32])
    fun `before API 33 the logic is skipped while disabled`() {
        val activity =
            Robolectric
                .buildActivity(AppCompatActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        var backs = 0
        val enabled = MutableStateFlow(true)
        Fragment().addTo(activity).addOnBackLogic(enabled) { backs++ }
        enabled.value = false
        idle()

        activity.onBackPressedDispatcher.onBackPressed()

        backs shouldBe 0
        activity.isFinishing.shouldBeTrue()
    }
}
