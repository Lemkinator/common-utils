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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import de.lemke.commonutils.ui.utils.autoCleared
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** Fragment with a real view so [viewLifecycleOwner] is available. */
internal class AutoClearedViewFragment : Fragment() {
    var initCount = 0
    val cached: String by autoCleared { "val${++initCount}" }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FrameLayout(requireContext())
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class AutoClearedUtilsRobolectricTest {
    private fun launchFragment(): AutoClearedViewFragment {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val fragment = AutoClearedViewFragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()
        return fragment
    }

    @Test
    fun `getValue initializes and caches the value`() {
        val fragment = launchFragment()
        // First call: runs initialize(), caches result, registers lifecycle observer
        val v1 = fragment.cached
        // Second call: returns cached value (fast-path) — initCount stays at 1
        val v2 = fragment.cached
        v1 shouldBe "val1"
        v2 shouldBe "val1"
        fragment.initCount shouldBe 1
    }

    @Test
    fun `onDestroy clears cached value and accessing after view destroy throws`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val fragment = AutoClearedViewFragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()
        // Access value to populate cache and register observer
        fragment.cached shouldBe "val1"
        fragment.initCount shouldBe 1
        // Remove the fragment → triggers onDestroyView → observer's onDestroy fires → clears cache
        activity.supportFragmentManager
            .beginTransaction()
            .remove(fragment)
            .commitNow()
        // View is null — accessing a view-bound property after onDestroyView is a programming error.
        shouldThrow<IllegalStateException> { fragment.cached }
        fragment.initCount shouldBe 1
    }
}
