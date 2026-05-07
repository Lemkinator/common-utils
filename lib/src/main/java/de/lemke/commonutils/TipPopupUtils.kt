@file:Suppress("unused")

package de.lemke.commonutils

import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import de.lemke.commonutils.ui.widget.DimmingView
import de.lemke.commonutils.ui.widget.TouchBlockingView
import dev.oneuiproject.oneui.ktx.activity
import dev.oneuiproject.oneui.widget.TipPopup
import dev.oneuiproject.oneui.design.R as designR

private const val TAG = "TipPopupUtils"

fun View.showTouchBlockingTipPopup(
    @StringRes messageResId: Int,
    @StringRes actionTextResId: Int? = null,
    action: () -> Unit = {},
) = showTouchBlockingTipPopup(context.getString(messageResId), actionTextResId?.let { context.getString(it) }, action)

fun View.showTouchBlockingTipPopup(message: String, actionText: String? = null, action: () -> Unit = {}) {
    showTipPopupWithOverlay(TouchBlockingView(context), message, actionText, action).setOutsideTouchEnabled(false)
}

fun View.showDimmingTipPopup(
    @StringRes messageResId: Int,
    @StringRes actionTextResId: Int? = null,
    action: () -> Unit = {},
) = showDimmingTipPopup(context.getString(messageResId), actionTextResId?.let { context.getString(it) }, action)

fun View.showDimmingTipPopup(message: String, actionText: String? = null, action: () -> Unit = {}) {
    showTipPopupWithOverlay(DimmingView(context), message, actionText, action)
}

private fun View.showTipPopupWithOverlay(overlay: View, message: String, actionText: String?, action: () -> Unit): TipPopup {
    val rootView = context.activity?.window?.decorView?.rootView as ViewGroup
    rootView.addView(overlay)
    return showTipPopup(rootView, overlay, message, actionText, action)
}

private fun View.showTipPopup(
    rootView: ViewGroup,
    backgroundView: View,
    message: String,
    actionText: String?,
    action: () -> Unit,
) = TipPopup(this, TipPopup.Mode.NORMAL).apply {
    setMessage(message)
    setExpanded(true)
    setBackgroundColorWithAlpha(context.getColor(designR.color.oui_des_background_color))
    setOnDismissListener { rootView.removeView(backgroundView) }
    setAction(actionText ?: context.getString(R.string.commonutils_ok)) { rootView.removeView(backgroundView); action() }
    show()
}
