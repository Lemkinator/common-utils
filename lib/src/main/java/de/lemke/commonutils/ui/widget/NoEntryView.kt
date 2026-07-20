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
package de.lemke.commonutils.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import de.lemke.commonutils.R
import de.lemke.commonutils.ui.utils.DEFAULT_LOTTIE_DELAY
import de.lemke.commonutils.ui.utils.play
import dev.oneuiproject.oneui.widget.RoundedLinearLayout

/** Empty-state view that shows a Lottie animation and a label when a list has no entries. */
class NoEntryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : RoundedLinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    /** The Lottie animation view displayed in the center of this widget. */
    val lottieAnimationView: LottieAnimationView by lazy { findViewById(R.id.noEntryLottie) }

    /** The label displayed below the animation. */
    val textView: TextView by lazy { findViewById(R.id.noEntryText) }

    init {
        inflate(context, R.layout.widget_no_entry_view, this)
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.NoEntryView) {
                getText(R.styleable.NoEntryView_noEntryText)?.let { textView.text = it }
            }
        }
        hide()
    }

    /** The label text shown below the animation. */
    var text: String
        get() = textView.text.toString()
        set(value) {
            textView.text = value
        }

    /** Toggles visibility; if shown, hides [otherView], and vice versa. */
    fun toggle(otherView: View? = null) = updateVisibility(!isVisible, otherView)

    /** Shows this view when [list] is empty, hides it (and shows [otherView]) otherwise. */
    fun updateVisibilityWith(
        list: List<*>,
        otherView: View? = null,
    ) = updateVisibility(list.isEmpty(), otherView)

    /** Sets this view visible or hidden; when shown hides [otherView], when hidden shows [otherView]. */
    fun updateVisibility(
        visible: Boolean,
        otherView: View? = null,
    ) = if (visible) {
        otherView?.isVisible = false
        show()
    } else {
        hide()
        otherView?.isVisible = true
    }

    /** Makes this view visible and starts the Lottie animation after a short delay. */
    fun show() {
        isVisible = true
        lottieAnimationView.play(delay = DEFAULT_LOTTIE_DELAY)
    }

    /** Hides this view. */
    fun hide() {
        isVisible = false
    }
}
