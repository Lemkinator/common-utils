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
package de.lemke.commonutils.ui.utils

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.Button
import androidx.annotation.ColorInt
import androidx.appcompat.R.color.sesl_show_button_shapes_color_disabled
import androidx.core.graphics.toColor
import de.lemke.commonutils.R
import kotlin.math.roundToInt

private const val MAX_CHANNEL_VALUE = 255

// Point where WCAG contrast to black equals contrast to white: sqrt(1.05*0.05)-0.05.
private const val CONTRASTING_TEXT_LUMINANCE_THRESHOLD = 0.17912878474

private fun compositeChannel(
    src: Int,
    srcAlpha: Float,
    dst: Int,
): Int = (src * srcAlpha + dst * (1f - srcAlpha)).roundToInt()

/** Composites this (possibly translucent) color over the opaque [over] backdrop, source-over with straight alpha. */
private fun Int.compositeOver(
    @ColorInt over: Int,
): Int {
    val srcAlpha = Color.alpha(this) / MAX_CHANNEL_VALUE.toFloat()
    return Color.rgb(
        compositeChannel(Color.red(this), srcAlpha, Color.red(over)),
        compositeChannel(Color.green(this), srcAlpha, Color.green(over)),
        compositeChannel(Color.blue(this), srcAlpha, Color.blue(over)),
    )
}

/**
 * Returns [Color.BLACK] or [Color.WHITE], whichever contrasts more with [background].
 *
 * [background] may be translucent: it is composited over the opaque [over] backdrop (source-over, straight
 * alpha, default [Color.WHITE]) before measuring luminance, since [android.graphics.Color.luminance] ignores alpha.
 */
@ColorInt
fun contrastingTextColor(
    @ColorInt background: Int,
    @ColorInt over: Int = Color.WHITE,
): Int {
    val luminance = background.compositeOver(over).toColor().luminance()
    return if (luminance >= CONTRASTING_TEXT_LUMINANCE_THRESHOLD) Color.BLACK else Color.WHITE
}

/**
 * Fills this button with [color] as a swatch preview: tints the background and sets a readable text color.
 * When [enabled] is `false`, both are replaced by the OneUI disabled button-shape presentation instead of [color].
 */
@SuppressLint("PrivateResource")
fun Button.bindColorSwatch(
    @ColorInt color: Int,
    enabled: Boolean = true,
) {
    isEnabled = enabled
    if (enabled) {
        backgroundTintList = ColorStateList.valueOf(color)
        setTextColor(contrastingTextColor(color))
    } else {
        backgroundTintList = ColorStateList.valueOf(context.getColor(sesl_show_button_shapes_color_disabled))
        setTextColor(context.getColor(R.color.commonutils_secondary_text_icon_color))
    }
}
