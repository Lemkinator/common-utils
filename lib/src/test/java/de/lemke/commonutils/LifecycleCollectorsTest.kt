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

import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.launchAndRepeatWithLifecycle
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LifecycleCollectorsTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private fun launchActivity(): AppCompatActivity =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .create()
            .start()
            .resume()
            .track(destroyActivities)
            .get()

    private fun idle() = Shadows.shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `collectState delivers initial value`() {
        val activity = launchActivity()
        val stateFlow = MutableStateFlow(42)
        val stateCollected = mutableListOf<Int>()
        activity.collectState(stateFlow) { stateCollected.add(it) }
        idle()
        stateCollected shouldBe listOf(42)
    }

    @Test
    fun `collectState delivers updated value`() {
        val activity = launchActivity()
        val stateFlow = MutableStateFlow(42)
        val stateCollected = mutableListOf<Int>()
        activity.collectState(stateFlow) { stateCollected.add(it) }
        idle()

        stateFlow.value = 99
        idle()
        stateCollected shouldBe listOf(42, 99)
    }

    @Test
    fun `launchAndRepeatWithLifecycle runs the block once the activity is started`() {
        val activity = launchActivity()
        var runs = 0

        activity.launchAndRepeatWithLifecycle { runs++ }
        idle()

        runs shouldBe 1
    }

    @Test
    fun `launchAndRepeatWithLifecycle with RESUMED waits for the resumed state`() {
        val controller =
            Robolectric
                .buildActivity(AppCompatActivity::class.java)
                .create()
                .start()
                .track(destroyActivities)
        var runs = 0

        controller.get().launchAndRepeatWithLifecycle(Lifecycle.State.RESUMED) { runs++ }
        idle()
        runs shouldBe 0

        controller.resume()
        idle()
        runs shouldBe 1
    }
}
