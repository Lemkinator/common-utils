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
import android.view.MenuItem
import com.google.android.material.navigation.NavigationView
import dev.oneuiproject.oneui.navigation.widget.DrawerNavigationView
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class DrawerUtilsRobolectricTest {
    @Test
    fun `saveSearchAndActionMode sets search mode key`() {
        val bundle = Bundle()
        bundle.saveSearchAndActionMode(isSearchMode = true)
        bundle.getBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE).shouldBeTrue()
        bundle.getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE).shouldBeFalse()
    }

    @Test
    fun `saveSearchAndActionMode sets action mode key and selected ids`() {
        val bundle = Bundle()
        bundle.saveSearchAndActionMode(isActionMode = true, selectedIds = setOf(1L, 2L, 3L))
        bundle.getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE).shouldBeTrue()
        bundle.getLongArray(COMMONUTILS_KEY_SELECTED_IDS)?.toSet() shouldBe setOf(1L, 2L, 3L)
    }

    @Test
    fun `saveSearchAndActionMode with defaults sets no keys`() {
        val bundle = Bundle()
        bundle.saveSearchAndActionMode()
        bundle.getBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE).shouldBeFalse()
        bundle.getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE).shouldBeFalse()
    }

    @Test
    fun `restoreSearchAndActionMode calls bundleIsNull when null`() {
        var called = false
        null.restoreSearchAndActionMode(bundleIsNull = { called = true })
        called.shouldBeTrue()
    }

    @Test
    fun `restoreSearchAndActionMode calls onSearchMode when search mode set`() {
        val bundle = Bundle().apply { saveSearchAndActionMode(isSearchMode = true) }
        var searchCalled = false
        bundle.restoreSearchAndActionMode(onSearchMode = { searchCalled = true })
        searchCalled.shouldBeTrue()
    }

    @Test
    fun `restoreSearchAndActionMode calls onActionMode with correct ids`() {
        val ids = setOf(10L, 20L)
        val bundle = Bundle().apply { saveSearchAndActionMode(isActionMode = true, selectedIds = ids) }
        var restoredIds: Set<Long>? = null
        bundle.restoreSearchAndActionMode(onActionMode = { restoredIds = it })
        restoredIds shouldBe ids
    }

    @Test
    fun `restoreSearchAndActionMode calls neither callback for empty bundle`() {
        var searchCalled = false
        var actionCalled = false
        Bundle().restoreSearchAndActionMode(
            onSearchMode = { searchCalled = true },
            onActionMode = { actionCalled = true },
        )
        searchCalled.shouldBeFalse()
        actionCalled.shouldBeFalse()
    }

    @Test
    fun `onNavigationSingleClick first click is allowed and rapid repeat is blocked`() {
        val navView = mockk<DrawerNavigationView>()
        val listenerSlot = slot<NavigationView.OnNavigationItemSelectedListener>()
        every { navView.setNavigationItemSelectedListener(capture(listenerSlot)) } answers { }
        val item = mockk<MenuItem>()
        var delegateCallCount = 0
        // Advance Robolectric's monotonic clock so elapsedRealtime() >> interval before the first click.
        android.os.SystemClock.sleep(1_000_001L)
        navView.onNavigationSingleClick(interval = 1_000_000L) {
            delegateCallCount++
            true
        }
        listenerSlot.captured.onNavigationItemSelected(item) // first: allowed (elapsed >> interval)
        delegateCallCount shouldBe 1
        listenerSlot.captured.onNavigationItemSelected(item) // immediate repeat: blocked
        delegateCallCount shouldBe 1
    }
}
