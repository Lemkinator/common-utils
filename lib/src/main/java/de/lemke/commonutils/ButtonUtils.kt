@file:Suppress("unused")

package de.lemke.commonutils

import android.util.Log
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat

fun AppCompatButton.setStyle(style: Int) = when (style) {
    R.style.ButtonStyle_Colored -> {
        backgroundTintList = ContextCompat.getColorStateList(context, R.color.primary_color_themed)
        TextViewCompat.setCompoundDrawableTintList(
            this,
            ContextCompat.getColorStateList(context, R.color.commonutils_primary_text_icon_color)
        )
        setTextColor(ContextCompat.getColor(context, R.color.commonutils_primary_text_icon_color))
    }
    R.style.ButtonStyle_Transparent -> {
        backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.transparent)
        TextViewCompat.setCompoundDrawableTintList(
            this,
            ContextCompat.getColorStateList(context, R.color.commonutils_primary_text_icon_color)
        )
        setTextColor(ContextCompat.getColorStateList(context, R.color.commonutils_primary_text_icon_color))
    }
    R.style.ButtonStyle_Transparent_Colored -> {
        backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.transparent)
        TextViewCompat.setCompoundDrawableTintList(
            this,
            ContextCompat.getColorStateList(context, R.color.primary_color_themed)
        )
        setTextColor(ContextCompat.getColorStateList(context, R.color.primary_color_themed))
    }
    R.style.ButtonStyle_Filled -> {
        backgroundTintList = ContextCompat.getColorStateList(context, R.color.commonutils_filled_background_color)
        TextViewCompat.setCompoundDrawableTintList(
            this,
            ContextCompat.getColorStateList(context, R.color.commonutils_primary_text_icon_color)
        )
        setTextColor(ContextCompat.getColor(context, R.color.commonutils_primary_text_icon_color))
    }
    R.style.ButtonStyle_Filled_Themed -> {
        backgroundTintList = ContextCompat.getColorStateList(context, R.color.commonutils_filled_background_color_themed)
        TextViewCompat.setCompoundDrawableTintList(
            this,
            ContextCompat.getColorStateList(context, R.color.commonutils_primary_text_icon_color_themed)
        )
        setTextColor(ContextCompat.getColor(context, R.color.commonutils_primary_text_icon_color_themed))
    }
    else -> Log.e("ButtonUtils", "Unknown style $style")
}
