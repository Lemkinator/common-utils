@file:Suppress("unused")

package de.lemke.commonutils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.DialogInterface
import android.os.Bundle
import android.os.IBinder
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import dev.oneuiproject.oneui.utils.DialogUtils

private const val TAG = "BasicUtils"

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun Fragment.toast(msg: String) = requireContext().toast(msg)

fun Context.toast(@StringRes stringResId: Int) {
    Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show()
}

fun Fragment.toast(@StringRes stringResId: Int) = requireContext().toast(stringResId)

fun Fragment.hideSoftInput(windowToken: IBinder? = null, flags: Int? = null) = requireActivity().hideSoftInput(windowToken, flags)

fun Activity.hideSoftInput(windowToken: IBinder? = null, flags: Int? = null) {
    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
        windowToken ?: currentFocus?.windowToken,
        flags ?: InputMethodManager.HIDE_NOT_ALWAYS
    )
}

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

fun Bundle.saveSearchAndActionMode(
    isSearchMode: Boolean = false,
    isActionMode: Boolean = false,
    selectedIds: LongArray = longArrayOf()
) {
    if (isSearchMode) {
        putBoolean("common-utils_isSearchMode", true)
    }
    if (isActionMode) {
        putBoolean("common-utils_isActionMode", true)
        putLongArray("common-utils_selectedIds", selectedIds)
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
        if (getBoolean("common-utils_isSearchMode")) {
            onSearchMode()
        }
        if (getBoolean("common-utils_isActionMode")) {
            onActionMode(getLongArray("common-utils_selectedIds")?.toTypedArray() ?: emptyArray())
        }
    }
}