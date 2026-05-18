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
package de.lemke.commonutils.ui.widget

import android.view.View
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoEntryViewTest {
    private fun view() = NoEntryView(ApplicationProvider.getApplicationContext())

    @Test
    fun `initially hidden`() {
        assertThat(view().isVisible).isFalse()
    }

    @Test
    fun `hide makes view invisible`() {
        val v = view()
        v.updateVisibility(true)
        v.hide()
        assertThat(v.isVisible).isFalse()
    }

    @Test
    fun `updateVisibility false keeps view hidden`() {
        val v = view()
        v.updateVisibility(false)
        assertThat(v.isVisible).isFalse()
    }

    @Test
    fun `updateVisibility true shows view`() {
        val v = view()
        v.updateVisibility(true)
        assertThat(v.isVisible).isTrue()
    }

    @Test
    fun `updateVisibilityWith empty list shows view`() {
        val v = view()
        v.updateVisibilityWith(emptyList<String>())
        assertThat(v.isVisible).isTrue()
    }

    @Test
    fun `updateVisibilityWith non-empty list hides view`() {
        val v = view()
        v.updateVisibility(true)
        v.updateVisibilityWith(listOf("item"))
        assertThat(v.isVisible).isFalse()
    }

    @Test
    fun `toggle shows hidden view`() {
        val v = view() // starts hidden
        v.toggle()
        assertThat(v.isVisible).isTrue()
    }

    @Test
    fun `toggle hides visible view`() {
        val v = view()
        v.updateVisibility(true)
        v.toggle()
        assertThat(v.isVisible).isFalse()
    }

    @Test
    fun `text property round-trips`() {
        val v = view()
        v.text = "No results found"
        assertThat(v.text).isEqualTo("No results found")
    }

    @Test
    fun `updateVisibility true hides companion view`() {
        val v = view()
        val other = View(ApplicationProvider.getApplicationContext()).also { it.isVisible = true }
        v.updateVisibility(true, other)
        assertThat(v.isVisible).isTrue()
        assertThat(other.isVisible).isFalse()
    }

    @Test
    fun `updateVisibility false shows companion view`() {
        val v = view()
        val other = View(ApplicationProvider.getApplicationContext()).also { it.isVisible = false }
        v.updateVisibility(true)
        v.updateVisibility(false, other)
        assertThat(v.isVisible).isFalse()
        assertThat(other.isVisible).isTrue()
    }
}
