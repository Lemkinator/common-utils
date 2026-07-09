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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LifecycleCollectorsTest {
    @Test
    fun `lifecycle collectors pin contract`() {
        val activity =
            Robolectric
                .buildActivity(AppCompatActivity::class.java)
                .create()
                .start()
                .resume()
                .get()
        val idle = { Shadows.shadowOf(Looper.getMainLooper()).idle() }

        val stateFlow = MutableStateFlow(42)
        val stateCollected = mutableListOf<Int>()
        activity.collectState(stateFlow) { stateCollected.add(it) }
        idle()
        stateCollected shouldBe listOf(42)

        stateFlow.value = 99
        idle()
        stateCollected shouldBe listOf(42, 99)

        val channel = Channel<String>(Channel.BUFFERED)
        val eventCollected = mutableListOf<String>()
        channel.trySend("hello")
        activity.collectEvents(channel.receiveAsFlow()) { eventCollected.add(it) }
        idle()
        eventCollected shouldBe listOf("hello")
    }
}
