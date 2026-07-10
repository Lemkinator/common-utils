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
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoEntryViewTest {
    private fun view() = NoEntryView(ApplicationProvider.getApplicationContext())

    @Test
    fun `initially hidden`() {
        view().isVisible shouldBe false
    }

    @Test
    fun `hide makes view invisible`() {
        val v = view()
        v.updateVisibility(true)
        v.hide()
        v.isVisible shouldBe false
    }

    @Test
    fun `updateVisibility false keeps view hidden`() {
        val v = view()
        v.updateVisibility(false)
        v.isVisible shouldBe false
    }

    @Test
    fun `updateVisibility true shows view`() {
        val v = view()
        v.updateVisibility(true)
        v.isVisible shouldBe true
    }

    @Test
    fun `updateVisibilityWith empty list shows view`() {
        val v = view()
        v.updateVisibilityWith(emptyList<String>())
        v.isVisible shouldBe true
    }

    @Test
    fun `updateVisibilityWith non-empty list hides view`() {
        val v = view()
        v.updateVisibility(true)
        v.updateVisibilityWith(listOf("item"))
        v.isVisible shouldBe false
    }

    @Test
    fun `toggle shows hidden view`() {
        val v = view()
        v.toggle()
        v.isVisible shouldBe true
    }

    @Test
    fun `toggle hides visible view`() {
        val v = view()
        v.updateVisibility(true)
        v.toggle()
        v.isVisible shouldBe false
    }

    @Test
    fun `text property round-trips`() {
        val v = view()
        v.text = "No results found"
        v.text shouldBe "No results found"
    }

    @Test
    fun `attrs constructor covers attrs-not-null branch`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val attrs = Robolectric.buildAttributeSet().build()
        NoEntryView(ctx, attrs).isVisible shouldBe false
    }

    @Test
    fun `attrs constructor with noEntryText attribute covers getText non-null let branch`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Adding noEntryText attribute → getText returns non-null → let block executes
        val attrs =
            Robolectric
                .buildAttributeSet()
                .addAttribute(de.lemke.commonutils.R.attr.noEntryText, "No results")
                .build()
        val v = NoEntryView(ctx, attrs)
        v.text shouldBe "No results"
    }

    @Test
    fun `four-arg constructor with explicit defStyleAttr and defStyleRes does not throw`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val attrs = Robolectric.buildAttributeSet().build()
        NoEntryView(ctx, attrs, 0, 0).isVisible shouldBe false
    }

    @Test
    fun `updateVisibility true hides companion view`() {
        val v = view()
        val other = View(ApplicationProvider.getApplicationContext()).also { it.isVisible = true }
        v.updateVisibility(true, other)
        v.isVisible shouldBe true
        other.isVisible shouldBe false
    }

    @Test
    fun `updateVisibility false shows companion view`() {
        val v = view()
        val other = View(ApplicationProvider.getApplicationContext()).also { it.isVisible = false }
        v.updateVisibility(true)
        v.updateVisibility(false, other)
        v.isVisible shouldBe false
        other.isVisible shouldBe true
    }

    @Test
    fun `show twice does not crash`() {
        val v = view()
        v.show()
        v.show()
        v.isVisible shouldBe true
    }
}
