@file:Suppress("unused")

package de.lemke.commonutils

import android.widget.ImageButton
import androidx.appcompat.content.res.AppCompatResources
import dev.oneuiproject.oneui.layout.NavDrawerLayout
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
