@file:Suppress("unused")

package de.lemke.commonutils

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.DialogInterface
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.collection.ScatterSet
import androidx.collection.emptyScatterSet
import androidx.fragment.app.Fragment
import dev.oneuiproject.oneui.utils.DialogUtils

private const val TAG = "BasicUtils"

fun Fragment.toast(msg: String) = requireContext().toast(msg)
fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
fun Fragment.toast(@StringRes stringResId: Int) = requireContext().toast(stringResId)
fun Context.toast(@StringRes stringResId: Int) = Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show()

fun Fragment.deleteAppDataAndExit(title: String? = null, message: String? = null, cancel: String? = null, ok: String? = null): Boolean {
    val dialog = AlertDialog.Builder(requireContext())
        .setTitle(title ?: getString(R.string.delete_appdata_and_exit))
        .setMessage(message ?: getString(R.string.delete_appdata_and_exit_warning))
        .setNegativeButton(cancel ?: getString(R.string.sesl_cancel), null)
        .setPositiveButton(ok ?: getString(R.string.ok)) { _: DialogInterface, _: Int ->
            (requireContext().getSystemService(ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
        }
        .create()
    dialog.show()
    DialogUtils.setDialogButtonTextColor(
        dialog,
        DialogInterface.BUTTON_POSITIVE,
        resources.getColor(dev.oneuiproject.oneui.design.R.color.oui_functional_red_color, requireContext().theme)
    )
    return true
}

const val COMMONUTILS_KEY_IS_SEARCH_MODE = "commonutils_isSearchMode"
const val COMMONUTILS_KEY_IS_ACTION_MODE = "commonutils_isActionMode"
const val COMMONUTILS_KEY_SELECTED_IDS = "commonutils_selectedIds"

fun Bundle.saveSearchAndActionMode(isSearchMode: Boolean = false) = saveSearchAndActionMode(isSearchMode, false, longArrayOf())

fun Bundle.saveSearchAndActionMode(
    isSearchMode: Boolean = false,
    isActionMode: Boolean = false,
    selectedIds: ScatterSet<Long> = emptyScatterSet<Long>()
) = saveSearchAndActionMode(isSearchMode, isActionMode, selectedIds.asSet().toLongArray())

fun Bundle.saveSearchAndActionMode(
    isSearchMode: Boolean = false,
    isActionMode: Boolean = false,
    selectedIds: LongArray = longArrayOf()
) {
    if (isSearchMode) {
        putBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE, true)
    }
    if (isActionMode) {
        putBoolean(COMMONUTILS_KEY_IS_ACTION_MODE, true)
        putLongArray(COMMONUTILS_KEY_SELECTED_IDS, selectedIds)
    }
}

inline fun Bundle?.restoreSearchAndActionMode(
    crossinline onSearchMode: () -> Unit = {},
    crossinline onActionMode: (selectedIds: Array<Long>) -> Unit = {},
    crossinline bundleIsNull: () -> Unit = {}
) {
    if (this == null) {
        bundleIsNull()
    } else {
        if (getBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE)) {
            onSearchMode()
        }
        if (getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE)) {
            onActionMode(getLongArray(COMMONUTILS_KEY_SELECTED_IDS)?.toTypedArray() ?: emptyArray())
        }
    }
}

