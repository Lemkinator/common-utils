@file:Suppress("unused")

package de.lemke.commonutils

import android.util.Log
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColorStateList
import androidx.core.widget.TextViewCompat.setCompoundDrawableTintList

private const val TAG = "ButtonUtils"
fun AppCompatButton.setStyle(style: Int) = when (style) {
    R.style.ButtonStyle_Colored -> {
        backgroundTintList = getColorStateList(context, R.color.primary_color_themed)
        setCompoundDrawableTintList(this, getColorStateList(context, R.color.commonutils_primary_text_icon_color))
        setTextColor(ContextCompat.getColor(context, R.color.commonutils_primary_text_icon_color))
    }

    R.style.ButtonStyle_Transparent -> {
        backgroundTintList = getColorStateList(context, android.R.color.transparent)
        setCompoundDrawableTintList(this, getColorStateList(context, R.color.commonutils_primary_text_icon_color))
        setTextColor(getColorStateList(context, R.color.commonutils_primary_text_icon_color))
    }

    R.style.ButtonStyle_Transparent_Colored -> {
        backgroundTintList = getColorStateList(context, android.R.color.transparent)
        setCompoundDrawableTintList(this, getColorStateList(context, R.color.primary_color_themed))
        setTextColor(getColorStateList(context, R.color.primary_color_themed))
    }

    R.style.ButtonStyle_Filled -> {
        backgroundTintList = getColorStateList(context, R.color.commonutils_filled_background_color)
        setCompoundDrawableTintList(this, getColorStateList(context, R.color.commonutils_primary_text_icon_color))
        setTextColor(ContextCompat.getColor(context, R.color.commonutils_primary_text_icon_color))
    }

    R.style.ButtonStyle_Filled_Themed -> {
        backgroundTintList = getColorStateList(context, R.color.commonutils_filled_background_color_themed)
        setCompoundDrawableTintList(this, getColorStateList(context, R.color.commonutils_primary_text_icon_color_themed))
        setTextColor(ContextCompat.getColor(context, R.color.commonutils_primary_text_icon_color_themed))
    }

    else -> Log.e(TAG, "Unknown style $style")
}

