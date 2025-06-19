package de.lemke.commonutils.ui.widget

import android.content.Context
import android.util.AttributeSet

open class TouchBlockingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : DimmingView(context, attrs) {
    init {
        isClickable = true
        isFocusable = true
    }
}