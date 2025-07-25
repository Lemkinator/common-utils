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
    val rootView = context.activity?.window?.decorView?.rootView as ViewGroup
    val blockingView = TouchBlockingView(context)
    rootView.addView(blockingView)
    showTipPopup(rootView, blockingView, message, actionText, action).apply { setOutsideTouchEnabled(false) }
}

fun View.showDimmingTipPopup(
    @StringRes messageResId: Int,
    @StringRes actionTextResId: Int? = null,
    action: () -> Unit = {},
) = showDimmingTipPopup(context.getString(messageResId), actionTextResId?.let { context.getString(it) }, action)

fun View.showDimmingTipPopup(message: String, actionText: String? = null, action: () -> Unit = {}) {
    val rootView = context.activity?.window?.decorView?.rootView as ViewGroup
    val dimmingView = DimmingView(context)
    rootView.addView(dimmingView)
    showTipPopup(rootView, dimmingView, message, actionText, action)
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

