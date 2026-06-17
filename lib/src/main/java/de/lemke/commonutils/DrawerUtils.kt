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
@file:Suppress("unused")

package de.lemke.commonutils

import android.os.Bundle

private const val TAG = "DrawerUtils"

/** Bundle key for persisting search mode state. */
const val COMMONUTILS_KEY_IS_SEARCH_MODE = "commonutils_isSearchMode"

/** Bundle key for persisting action mode state. */
const val COMMONUTILS_KEY_IS_ACTION_MODE = "commonutils_isActionMode"

/** Bundle key for persisting the set of selected item IDs during action mode. */
const val COMMONUTILS_KEY_SELECTED_IDS = "commonutils_selectedIds"

/** Saves search and action mode state into this bundle for later restoration via [restoreSearchAndActionMode]. */
fun Bundle.saveSearchAndActionMode(
    isSearchMode: Boolean = false,
    isActionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
) {
    if (isSearchMode) {
        putBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE, true)
    }
    if (isActionMode) {
        putBoolean(COMMONUTILS_KEY_IS_ACTION_MODE, true)
        putLongArray(COMMONUTILS_KEY_SELECTED_IDS, selectedIds.toLongArray())
    }
}

/** Restores search and action mode state from this bundle, invoking the provided callbacks as needed. */
@NoCoverage
inline fun Bundle?.restoreSearchAndActionMode(
    crossinline onSearchMode: () -> Unit = {},
    crossinline onActionMode: (selectedIds: Set<Long>) -> Unit = {},
    crossinline bundleIsNull: () -> Unit = {},
) {
    if (this == null) {
        bundleIsNull()
    } else {
        if (getBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE)) {
            onSearchMode()
        }
        if (getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE)) {
            onActionMode(getLongArray(COMMONUTILS_KEY_SELECTED_IDS)?.toSet() ?: emptySet())
        }
    }
}
