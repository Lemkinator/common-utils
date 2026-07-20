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
import de.lemke.commonutils.ui.utils.collectEvents
import de.lemke.commonutils.ui.utils.collectState
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LifecycleCollectorsTest {
    private fun launchActivity(): AppCompatActivity =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .create()
            .start()
            .resume()
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
    fun `collectEvents delivers buffered event`() {
        val activity = launchActivity()
        val channel = Channel<String>(Channel.BUFFERED)
        val eventCollected = mutableListOf<String>()
        channel.trySend("hello")
        activity.collectEvents(channel.receiveAsFlow()) { eventCollected.add(it) }
        idle()
        eventCollected shouldBe listOf("hello")
    }
}
