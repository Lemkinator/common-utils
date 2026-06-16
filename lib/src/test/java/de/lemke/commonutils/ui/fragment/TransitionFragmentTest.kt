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
package de.lemke.commonutils.ui.fragment

import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.transition.MaterialSharedAxis
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

internal class ConcreteTransitionFragment : TransitionFragment(0)

internal class ConcreteSharedAxisFragment : TransitionFragmentSharedAxis(0, MaterialSharedAxis.X)

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class TransitionFragmentTest {
    private fun activity(): AppCompatActivity =
        Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()

    @Test
    fun `TransitionFragment onCreate wires transitions`() {
        val activity = activity()
        val fragment = ConcreteTransitionFragment()
        activity.supportFragmentManager.beginTransaction().add(fragment, "tf").commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        fragment.enterTransition shouldNotBe null
    }

    @Test
    fun `TransitionFragmentSharedAxis onCreate wires MaterialSharedAxis transitions`() {
        val activity = activity()
        val fragment = ConcreteSharedAxisFragment()
        activity.supportFragmentManager.beginTransaction().add(fragment, "saf").commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        fragment.enterTransition shouldNotBe null
    }
}
