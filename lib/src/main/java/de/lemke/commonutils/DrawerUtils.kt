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

import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import dev.oneuiproject.oneui.layout.Badge
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import dev.oneuiproject.oneui.navigation.widget.DrawerNavigationView
import dev.oneuiproject.oneui.R as iconsR

private const val TAG = "DrawerUtils"
private const val NAV_RAIL_MIN_SIDE_MARGIN_DP = 14

/** Implemented by the host activity to expose the [NavDrawerLayout] to all hosted fragments. */
interface DrawerHost {
    val drawerLayout: NavDrawerLayout
}

/** Returns the [NavDrawerLayout] from the host activity. Requires the activity to implement [DrawerHost]. */
val Fragment.drawerLayout: NavDrawerLayout get() = (requireActivity() as DrawerHost).drawerLayout

/**
 * Clears [CoordinatorLayout]'s `mLastNestedScrollingChild` on the [DrawerHost]'s
 * [NavDrawerLayout] coordinator.
 *
 * **Why this exists:** Samsung's `AdaptiveCoordinatorLayout` stores a strong reference to the
 * last nested-scrolling child and never clears it when the child is removed from the window.
 * This prevents detached fragment view hierarchies from being GC'd after navigation.
 *
 * Call from [Fragment.onDestroyView] in any fragment with a scrollable view hosted inside a
 * [DrawerHost] activity.
 *
 * TODO Remove once sesl-androidx `CoordinatorLayout` uses `WeakReference<View>` for
 *  `mLastNestedScrollingChild` (fix tracked in sesl-androidx fix/memory-leaks).
 */
fun Fragment.clearLastNestedScrollingChild() {
    try {
        val coordinator = (requireActivity() as? DrawerHost)?.drawerLayout?.appBarLayout?.parent as? CoordinatorLayout
        val field = CoordinatorLayout::class.java.getDeclaredField("mLastNestedScrollingChild")
        field.isAccessible = true
        field.set(coordinator, null)
    } catch (_: Exception) {
    }
}

/** Sets up the drawer header button with a custom click handler and configures the nav rail. */
fun NavDrawerLayout.setupHeaderAndNavRail(
    aboutApp: String,
    onHeaderClick: () -> Unit,
) {
    setupHeaderButton(
        icon = AppCompatResources.getDrawable(context, iconsR.drawable.ic_oui_info_outline)!!,
        tooltipText = aboutApp,
        listener = { onHeaderClick() },
    )
    setNavRailContentMinSideMargin(NAV_RAIL_MIN_SIDE_MARGIN_DP)
    closeNavRailOnBack = true
    context.onAppUpdateAvailable { setButtonBadges(Badge.DOT, Badge.DOT) }
}

/** Wraps [listener] to ignore repeated clicks within [interval] milliseconds, preventing double-navigation. */
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
