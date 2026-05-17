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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BasicUtilsTest {
    // region saveSearchAndActionMode

    @Test
    fun `saveSearchAndActionMode stores search mode flag when true`() {
        val bundle = mockk<Bundle>(relaxed = true)
        bundle.saveSearchAndActionMode(isSearchMode = true)
        verify { bundle.putBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE, true) }
    }

    @Test
    fun `saveSearchAndActionMode omits search mode flag when false`() {
        val bundle = mockk<Bundle>(relaxed = true)
        bundle.saveSearchAndActionMode(isSearchMode = false)
        verify(exactly = 0) { bundle.putBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE, any()) }
    }

    @Test
    fun `saveSearchAndActionMode stores action mode flag and IDs when true`() {
        val bundle = mockk<Bundle>(relaxed = true)
        val ids = setOf(1L, 2L, 3L)
        bundle.saveSearchAndActionMode(isActionMode = true, selectedIds = ids)
        verify { bundle.putBoolean(COMMONUTILS_KEY_IS_ACTION_MODE, true) }
        verify { bundle.putLongArray(COMMONUTILS_KEY_SELECTED_IDS, ids.toLongArray()) }
    }

    @Test
    fun `saveSearchAndActionMode omits action mode data when false`() {
        val bundle = mockk<Bundle>(relaxed = true)
        bundle.saveSearchAndActionMode(isActionMode = false)
        verify(exactly = 0) { bundle.putBoolean(COMMONUTILS_KEY_IS_ACTION_MODE, any()) }
        verify(exactly = 0) { bundle.putLongArray(any(), any()) }
    }

    @Test
    fun `saveSearchAndActionMode stores both modes simultaneously`() {
        val bundle = mockk<Bundle>(relaxed = true)
        bundle.saveSearchAndActionMode(isSearchMode = true, isActionMode = true, selectedIds = setOf(42L))
        verify { bundle.putBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE, true) }
        verify { bundle.putBoolean(COMMONUTILS_KEY_IS_ACTION_MODE, true) }
    }

    // endregion

    // region restoreSearchAndActionMode

    @Test
    fun `restoreSearchAndActionMode calls onSearchMode when flag set`() {
        val bundle = mockk<Bundle>()
        every { bundle.getBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE) } returns true
        every { bundle.getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE) } returns false

        var called = false
        bundle.restoreSearchAndActionMode(onSearchMode = { called = true })
        assertTrue(called)
    }

    @Test
    fun `restoreSearchAndActionMode skips onSearchMode when flag not set`() {
        val bundle = mockk<Bundle>()
        every { bundle.getBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE) } returns false
        every { bundle.getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE) } returns false

        var called = false
        bundle.restoreSearchAndActionMode(onSearchMode = { called = true })
        assertFalse(called)
    }

    @Test
    fun `restoreSearchAndActionMode calls onActionMode with IDs`() {
        val bundle = mockk<Bundle>()
        every { bundle.getBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE) } returns false
        every { bundle.getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE) } returns true
        every { bundle.getLongArray(COMMONUTILS_KEY_SELECTED_IDS) } returns longArrayOf(10L, 20L)

        var receivedIds: Set<Long>? = null
        bundle.restoreSearchAndActionMode(onActionMode = { receivedIds = it })
        assertEquals(setOf(10L, 20L), receivedIds)
    }

    @Test
    fun `restoreSearchAndActionMode passes empty set when getLongArray returns null`() {
        val bundle = mockk<Bundle>()
        every { bundle.getBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE) } returns false
        every { bundle.getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE) } returns true
        every { bundle.getLongArray(COMMONUTILS_KEY_SELECTED_IDS) } returns null

        var receivedIds: Set<Long>? = null
        bundle.restoreSearchAndActionMode(onActionMode = { receivedIds = it })
        assertEquals(emptySet<Long>(), receivedIds)
    }

    @Test
    fun `restoreSearchAndActionMode calls bundleIsNull when receiver is null`() {
        var called = false
        val bundle: Bundle? = null
        bundle.restoreSearchAndActionMode(bundleIsNull = { called = true })
        assertTrue(called)
    }

    // endregion
}
