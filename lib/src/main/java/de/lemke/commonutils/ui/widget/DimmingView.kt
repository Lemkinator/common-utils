package de.lemke.commonutils.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.content.withStyledAttributes
import de.lemke.commonutils.R

open class DimmingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    companion object {
        private const val DEFAULT_ALPHA = 0.5f
    }

    init {
        if (attrs == null) {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setBackgroundColor((DEFAULT_ALPHA * 255.0f + 0.5f).toInt() shl 24)
        } else {
            context.withStyledAttributes(attrs, R.styleable.DimmingView) {
                setBackgroundColor((getFloat(R.styleable.DimmingView_alpha, DEFAULT_ALPHA) * 255.0f + 0.5f).toInt() shl 24)
            }
        }
    }
}