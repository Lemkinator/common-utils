@file:Suppress("unused")

package de.lemke.commonutils

import android.app.Activity
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.os.IBinder
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun Fragment.toast(msg: String) = requireContext().toast(msg)

fun Context.toast(@StringRes stringResId: Int) {
    Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show()
}

fun Fragment.toast(@StringRes stringResId: Int) = requireContext().toast(stringResId)

fun Activity.hideSoftInput(windowToken: IBinder? = null, flags: Int? = null) {
    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
        windowToken ?: currentFocus?.windowToken,
        flags ?: InputMethodManager.HIDE_NOT_ALWAYS
    )
}
