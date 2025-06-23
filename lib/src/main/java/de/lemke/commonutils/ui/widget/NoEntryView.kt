@file:Suppress("unused")

package de.lemke.commonutils.ui.widget

import android.content.Context
import android.graphics.ColorFilter
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty.COLOR_FILTER
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import de.lemke.commonutils.R
import dev.oneuiproject.oneui.widget.RoundedLinearLayout


class NoEntryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : RoundedLinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    val lottieAnimationView: LottieAnimationView by lazy { findViewById<LottieAnimationView>(R.id.noEntryLottie) }
    val textView: TextView by lazy { findViewById<TextView>(R.id.noEntryText) }


    init {
        inflate(context, R.layout.widget_no_entry_view, this)
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.NoEntryView) {
                getText(R.styleable.NoEntryView_noEntryText)?.let { textView.text = it }
            }
        }
        hide()
    }

    var text: String
        get() = textView.text.toString()
        set(value) {
            textView.text = value
        }

    fun toggle(otherView: View? = null) = updateVisibility(!isVisible, otherView)
    fun updateVisibilityWith(list: List<*>, otherView: View? = null) = updateVisibility(list.isEmpty(), otherView)
    fun updateVisibility(visible: Boolean, otherView: View? = null) = if (visible) {
        otherView?.isVisible = false
        show()
    } else {
        hide()
        otherView?.isVisible = true
    }

    fun show() {
        lottieAnimationView.cancelAnimation()
        lottieAnimationView.progress = 0f
        isVisible = true
        val callback = LottieValueCallback<ColorFilter>(SimpleColorFilter(context.getColor(R.color.primary_color_themed)))
        lottieAnimationView.addValueCallback(KeyPath("**"), COLOR_FILTER, callback)
        lottieAnimationView.postDelayed({ lottieAnimationView.playAnimation() }, 400)
    }

    fun hide() {
        isVisible = false
    }

}