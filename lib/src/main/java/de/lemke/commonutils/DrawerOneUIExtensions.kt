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
@file:Suppress("unused")

package de.lemke.commonutils

import android.widget.ImageButton
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.navigation.NavigationView
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import dev.oneuiproject.oneui.layout.Badge
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import dev.oneuiproject.oneui.navigation.widget.DrawerNavigationView
import dev.oneuiproject.oneui.R as iconsR
import dev.oneuiproject.oneui.design.R as designR

private const val NAV_RAIL_MIN_SIDE_MARGIN_DP = 14

/** Sets up the drawer header button with an info icon navigating to [CommonUtilsAboutActivity] and configures the nav rail. */
@NoCoverage
fun NavDrawerLayout.setupHeaderAndNavRail(aboutApp: String) {
    setupHeaderButton(
        icon = AppCompatResources.getDrawable(context, iconsR.drawable.ic_oui_info_outline)!!,
        tooltipText = aboutApp,
        listener = {
            findViewById<ImageButton>(designR.id.oui_des_drawer_header_button)
                .transformToActivity(CommonUtilsAboutActivity::class.java, transitionName = "CommonUtilsAboutAppTransition")
        },
    )
    setNavRailContentMinSideMargin(NAV_RAIL_MIN_SIDE_MARGIN_DP)
    closeNavRailOnBack = true
    context.onAppUpdateAvailable { setButtonBadges(Badge.DOT, Badge.DOT) }
}

/** Wraps [listener] to ignore repeated clicks within [interval] milliseconds, preventing double-navigation. */
@NoCoverage
fun DrawerNavigationView.onNavigationSingleClick(
    interval: Long = 600,
    listener: NavigationView.OnNavigationItemSelectedListener,
) {
    var lastClick = 0L
    setNavigationItemSelectedListener { item ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClick < interval) return@setNavigationItemSelectedListener false
        lastClick = currentTime
        listener.onNavigationItemSelected(item)
    }
}
