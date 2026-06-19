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

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.data.commonUtilsSettings
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class SearchUtilsRobolectricTest {
    @BeforeEach
    fun setUp() {
        val prefs =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("search_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        commonUtilsSettings = SettingsRepository(prefs)
    }

    private fun activity(): AppCompatActivity =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

    @Test
    fun `onQueryTextChange updates flow and returns true when flow not null`() {
        val flow = MutableStateFlow<String?>("initial")
        val listener = activity().getCommonUtilsSearchListener(flow)
        listener.onQueryTextChange("hello").shouldBeTrue()
        flow.value shouldBe "hello"
    }

    @Test
    fun `onQueryTextChange returns false when flow value is null`() {
        val flow = MutableStateFlow<String?>(null)
        val listener = activity().getCommonUtilsSearchListener(flow)
        listener.onQueryTextChange("hello").shouldBeFalse()
    }

    @Test
    fun `onQueryTextChange with null query sets flow to empty string`() {
        val flow = MutableStateFlow<String?>("old")
        val listener = activity().getCommonUtilsSearchListener(flow)
        listener.onQueryTextChange(null)
        flow.value shouldBe ""
    }

    @Test
    fun `onQueryTextSubmit updates flow and returns true`() {
        val flow = MutableStateFlow<String?>("initial")
        val listener = activity().getCommonUtilsSearchListener(flow)
        listener.onQueryTextSubmit("submitted").shouldBeTrue()
        flow.value shouldBe "submitted"
    }

    @Test
    fun `onSearchModeToggle with isActive false sets flow to null`() {
        val activity = activity()
        val flow = MutableStateFlow<String?>("query")
        val listener = activity.getCommonUtilsSearchListener(flow)
        val searchView = SearchView(activity)
        listener.onSearchModeToggle(searchView, false)
        flow.value.shouldBeNull()
    }

    @Test
    fun `onSearchModeToggle with isActive true restores saved search`() {
        val activity = activity()
        val flow = MutableStateFlow<String?>(null)
        val listener = activity.getCommonUtilsSearchListener(flow)
        val searchView = SearchView(activity)
        listener.onSearchModeToggle(searchView, true)
        flow.value shouldBe ""
    }

    @Test
    fun `onSearchModeToggle with non-null queryHint sets hint on searchView`() {
        val activity = activity()
        val flow = MutableStateFlow<String?>(null)
        val listener = activity.getCommonUtilsSearchListener(flow, queryHint = R.string.commonutils_search)
        val searchView = SearchView(activity)
        listener.onSearchModeToggle(searchView, true)
        searchView.queryHint.toString() shouldBe activity.getString(R.string.commonutils_search)
    }
}
