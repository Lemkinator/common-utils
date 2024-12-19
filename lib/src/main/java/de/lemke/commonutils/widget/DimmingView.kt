package de.lemke.commonutils.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import de.lemke.commonutils.R

class DimmingView(context: Context): View(context) {
    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(context.getColor(R.color.commonutils_dimmed_background))
    }
}