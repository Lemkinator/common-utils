@file:Suppress("unused")

package de.lemke.commonutils

import android.widget.ImageButton
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.navigation.NavigationView
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import dev.oneuiproject.oneui.navigation.widget.DrawerNavigationView
import dev.oneuiproject.oneui.R as iconsR
import dev.oneuiproject.oneui.design.R as designR

private const val TAG = "DrawerUtils"

fun NavDrawerLayout.setupHeaderAndNavRail(aboutApp: String) {
    setupHeaderButton(
        icon = AppCompatResources.getDrawable(context, iconsR.drawable.ic_oui_info_outline)!!,
        tooltipText = aboutApp,
        listener = { findViewById<ImageButton>(designR.id.oui_des_drawer_header_button).transformToActivity(AboutActivity::class.java) }
    )
    setNavRailContentMinSideMargin(14)
    closeNavRailOnBack = true
}

private var lastClick = 0L

fun DrawerNavigationView.onNavigationSingleClick(interval: Long = 600, listener: NavigationView.OnNavigationItemSelectedListener) {
    setNavigationItemSelectedListener { item ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClick < interval) return@setNavigationItemSelectedListener false
        lastClick = currentTime
        listener.onNavigationItemSelected(item)
    }
}
