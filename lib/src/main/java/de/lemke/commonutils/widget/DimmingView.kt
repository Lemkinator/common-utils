package de.lemke.commonutils.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import de.lemke.commonutils.R

open class DimmingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    companion object {
        private const val DEFAULT_ALPHA = 0.5f
    }

    init {
        if (attrs == null) {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor((DEFAULT_ALPHA * 255.0f + 0.5f).toInt() shl 24)
        } else {
            context.obtainStyledAttributes(attrs, R.styleable.DimmingView).apply {
                setBackgroundColor((getFloat(R.styleable.DimmingView_alpha, DEFAULT_ALPHA) * 255.0f + 0.5f).toInt() shl 24)
                recycle()
            }
        }
    }
}