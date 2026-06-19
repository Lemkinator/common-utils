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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
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

class ViewFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?,
    ): View = FrameLayout(requireContext())
}

private fun attachedFragment(): Fragment {
    val activity =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .create()
            .start()
            .resume()
            .get()
    val fragment = ViewFragment()
    activity.supportFragmentManager
        .beginTransaction()
        .add(android.R.id.content, fragment)
        .commitNow()
    return fragment
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FragmentCollectStateInitialTest {
    @Test
    fun `Fragment collectState delivers initial value`() {
        val fragment = attachedFragment()
        val flow = MutableStateFlow(10)
        val collected = mutableListOf<Int>()
        fragment.collectState(flow) { collected.add(it) }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        collected shouldBe listOf(10)
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FragmentCollectStateUpdateTest {
    @Test
    fun `Fragment collectState delivers updated value`() {
        val fragment = attachedFragment()
        val flow = MutableStateFlow(1)
        val collected = mutableListOf<Int>()
        fragment.collectState(flow) { collected.add(it) }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        flow.value = 2
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        collected shouldBe listOf(1, 2)
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FragmentCollectEventsTest {
    @Test
    fun `Fragment collectEvents delivers buffered event`() {
        val fragment = attachedFragment()
        val channel = Channel<String>(Channel.BUFFERED)
        val collected = mutableListOf<String>()
        channel.trySend("event-one")
        fragment.collectEvents(channel.receiveAsFlow()) { collected.add(it) }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        collected shouldBe listOf("event-one")
    }
}
