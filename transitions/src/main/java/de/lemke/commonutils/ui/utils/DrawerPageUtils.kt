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

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.annotation.MainThread
import androidx.reflect.DeviceInfo
import de.lemke.commonutils.transitions.NoCoverage
import dev.oneuiproject.oneui.ktx.activity
import dev.oneuiproject.oneui.ktx.startPopOverActivity
import dev.oneuiproject.oneui.popover.PopOverOptions
import dev.oneuiproject.oneui.utils.DeviceLayoutUtil

/**
 * Opens the page of [cls] from this drawer item or header button; see the [Intent] overload.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@MainThread
public fun View.launchDrawerPage(
    cls: Class<*>,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(context),
): Boolean = launchDrawerPage(Intent(context, cls), transitionName, popOverOptions)

/**
 * Opens the page of [T] from this drawer item or header button; see the [Intent] overload.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@NoCoverage
@MainThread
public inline fun <reified T : Activity> View.launchDrawerPage(
    transitionName: String = DEFAULT_TRANSITION_NAME,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(context),
): Boolean = launchDrawerPage(Intent(context, T::class.java), transitionName, popOverOptions)

/**
 * Opens the page of [intent] from this drawer item or header button, as a gated launch.
 *
 * On a One UI device whose launching activity has a smallest width of at least 600dp, the page starts as a pop-over
 * with [popOverOptions] and [Intent.FLAG_ACTIVITY_SINGLE_TOP]. Every other device, phones and non-One UI large
 * screens included, gets the container transform of [transformToActivity] with [transitionName]. [intent] stays
 * unchanged by the pop-over launch.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@MainThread
public fun View.launchDrawerPage(
    intent: Intent,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(context),
): Boolean {
    val activity = context.activity
    return if (activity != null && activity.showsPopOver()) {
        activity.launchGated {
            activity.startPopOverActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), popOverOptions)
        }
    } else {
        transformToActivity(intent, transitionName)
    }
}

// One UI starts a pop-over only in a task whose smallest width is at least 600dp.
private fun Activity.showsPopOver(): Boolean = DeviceInfo.isOneUI() && DeviceLayoutUtil.isTabletLayout(resources)
