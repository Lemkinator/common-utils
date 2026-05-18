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

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.oneuiproject.oneui.ktx.setOnClickListenerWithProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.oneuiproject.oneui.design.R as designR

private const val TAG = "BasicUtils"
private const val DELETE_APP_DATA_DELAY_MS = 500L

/** Shows a short toast with the given message. */
fun Fragment.toast(msg: String) = requireContext().toast(msg)

/** Shows a short toast with the given message. */
fun Context.toast(msg: String) = Toast.makeText(this, msg, LENGTH_SHORT).show()

/** Shows a short toast with the given string resource. */
fun Fragment.toast(
    @StringRes stringResId: Int,
) = requireContext().toast(stringResId)

/** Shows a short toast with the given string resource. */
fun Context.toast(
    @StringRes stringResId: Int,
) = Toast.makeText(this, stringResId, LENGTH_SHORT).show()

/** Launches [block] in the lifecycle scope, repeating it whenever the lifecycle reaches [minActiveState]. */
inline fun AppCompatActivity.launchAndRepeatWithLifecycle(
    minActiveState: State = STARTED,
    crossinline block: suspend CoroutineScope.() -> Unit,
) {
    lifecycleScope.launch { lifecycle.repeatOnLifecycle(minActiveState) { block() } }
}

/** Launches [block] in the view lifecycle scope, repeating it whenever the view lifecycle reaches [minActiveState]. */
inline fun Fragment.launchAndRepeatWithViewLifecycle(
    minActiveState: State = STARTED,
    crossinline block: suspend CoroutineScope.() -> Unit,
) {
    viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.lifecycle.repeatOnLifecycle(minActiveState) { block() } }
}

/** Shows a confirmation dialog and clears all application user data on confirmation. */
fun Fragment.deleteAppDataAndExit(
    title: String? = null,
    message: String? = null,
    cancel: String? = null,
    delete: String? = null,
) {
    val dialog =
        AlertDialog
            .Builder(requireContext())
            .setTitle(title ?: getString(R.string.commonutils_delete_appdata_and_exit))
            .setMessage(message ?: getString(R.string.commonutils_delete_appdata_and_exit_warning))
            .setNegativeButton(cancel ?: getString(designR.string.oui_des_common_cancel), null)
            .setPositiveButton(delete ?: getString(R.string.commonutils_delete), null)
            .create()
    dialog.show()
    dialog.getButton(BUTTON_POSITIVE).apply {
        setTextColor(requireContext().getColor(designR.color.oui_des_functional_red_color))
        setOnClickListenerWithProgress { button, progressBar ->
            lifecycleScope.launch {
                delay(DELETE_APP_DATA_DELAY_MS)
                (requireContext().getSystemService(ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
            }
        }
    }
}

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

/** Restores search and action mode state from this bundle, invoking the appropriate callback. */
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
