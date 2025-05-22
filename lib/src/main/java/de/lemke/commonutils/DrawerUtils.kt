@file:Suppress("unused")

package de.lemke.commonutils

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.layout.Badge
import dev.oneuiproject.oneui.layout.DrawerLayout.DrawerState.CLOSE
import dev.oneuiproject.oneui.layout.DrawerLayout.DrawerState.CLOSING
import dev.oneuiproject.oneui.layout.DrawerLayout.DrawerState.OPEN
import dev.oneuiproject.oneui.layout.DrawerLayout.DrawerState.OPENING
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import dev.oneuiproject.oneui.R as iconsR
import dev.oneuiproject.oneui.design.R as designR

private const val TAG = "DrawerUtils"

fun NavDrawerLayout.setup(
    aboutApp: String,
    drawerItemTitles: List<TextView>,
    drawerListView: LinearLayout,
) {
    setupHeaderAndNavRail(aboutApp)

    //setupNavRailFadeEffect
    if (isLargeScreenMode) {
        setDrawerStateListener {
            when (it) {
                OPEN -> offsetUpdaterJob?.cancel().also { updateOffset(drawerItemTitles, drawerListView, 1f) }
                CLOSE -> offsetUpdaterJob?.cancel().also { updateOffset(drawerItemTitles, drawerListView, 0f) }
                CLOSING, OPENING -> startOffsetUpdater(drawerItemTitles, drawerListView)
            }
        }
        //Set initial offset
        post { updateOffset(drawerItemTitles, drawerListView, drawerOffset) }
    }
    context.onAppUpdateAvailable { setButtonBadges(Badge.DOT, Badge.DOT) }
}

fun NavDrawerLayout.setupHeaderAndNavRail(aboutApp: String) {
    setHeaderButtonIcon(AppCompatResources.getDrawable(context, iconsR.drawable.ic_oui_info_outline))
    setHeaderButtonTooltip(aboutApp)
    setHeaderButtonOnClickListener {
        findViewById<ImageButton>(designR.id.oui_des_drawer_header_button).transformToActivity(AboutActivity::class.java)
    }
    setNavRailContentMinSideMargin(14)
    closeNavRailOnBack = true
}

private var offsetUpdaterJob: Job? = null
private fun NavDrawerLayout.startOffsetUpdater(drawerItemTitles: List<TextView>, drawerListView: LinearLayout) {
    //Ensure no duplicate job is running
    if (offsetUpdaterJob?.isActive == true) return
    offsetUpdaterJob = CoroutineScope(Dispatchers.Main).launch {
        while (isActive) {
            updateOffset(drawerItemTitles, drawerListView, drawerOffset)
            delay(50)
        }
    }
}

fun updateOffset(drawerItemTitles: List<TextView>, drawerListView: LinearLayout, offset: Float) {
    drawerItemTitles.forEach { it.alpha = offset }
    drawerListView.children.forEach {
        it.post {
            if (offset == 0f) {
                it.updateLayoutParams<MarginLayoutParams> {
                    width = if (it is LinearLayout) 52f.dpToPx(it.context.resources) //drawer item
                    else 25f.dpToPx(it.context.resources) //divider item
                }
            } else if (it.width != MATCH_PARENT) {
                it.updateLayoutParams<MarginLayoutParams> { width = MATCH_PARENT }
            }
        }
    }
}

