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
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.reflect.DeviceInfo
import de.lemke.commonutils.transitions.NoCoverage
import dev.oneuiproject.oneui.ktx.activity
import dev.oneuiproject.oneui.ktx.startPopOverActivity
import dev.oneuiproject.oneui.popover.PopOverOptions
import dev.oneuiproject.oneui.utils.DeviceLayoutUtil

/**
 * Opens [cls] as a pop-over or container transform; see the [Intent] overload.
 * @param duration The container transform duration in milliseconds; ignored on the pop-over arm.
 * @param fadeMode The container transform fade mode; ignored on the pop-over arm.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@MainThread
public fun View.popOverOrTransformToActivity(
    cls: Class<*>,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(context),
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
): Boolean = popOverOrTransformToActivity(Intent(context, cls), transitionName, popOverOptions, duration, fadeMode)

/**
 * Opens [T] as a pop-over or container transform; see the [Intent] overload.
 * @param duration The container transform duration in milliseconds; ignored on the pop-over arm.
 * @param fadeMode The container transform fade mode; ignored on the pop-over arm.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@NoCoverage
@MainThread
public inline fun <reified T : Activity> View.popOverOrTransformToActivity(
    transitionName: String = DEFAULT_TRANSITION_NAME,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(context),
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
): Boolean = popOverOrTransformToActivity(Intent(context, T::class.java), transitionName, popOverOptions, duration, fadeMode)

/**
 * Opens [intent] as a gated launch.
 * This view is the container transform origin; the pop-over position comes from [popOverOptions].
 *
 * On a One UI device whose launching activity has a smallest width of at least 600dp, the activity starts as a pop-over
 * with [popOverOptions] and [Intent.FLAG_ACTIVITY_SINGLE_TOP]. Every other device, phones and non-One UI large
 * screens included, gets the container transform of [transformToActivity] with [transitionName]. [intent] stays
 * unchanged by the pop-over launch.
 * @param duration The container transform duration in milliseconds; ignored on the pop-over arm.
 * @param fadeMode The container transform fade mode; ignored on the pop-over arm.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@MainThread
public fun View.popOverOrTransformToActivity(
    intent: Intent,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(context),
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
): Boolean {
    val activity = context.activity
    return if (activity != null && activity.showsPopOver()) {
        activity.launchPopOver(intent, popOverOptions)
    } else {
        transformToActivity(intent, transitionName, duration, fadeMode)
    }
}

/**
 * Opens [cls] as a pop-over or container transform, with [viewId] as the transform origin; see the [Intent] overload.
 * @param duration The container transform duration in milliseconds; ignored on the pop-over arm.
 * @param fadeMode The container transform fade mode; ignored on the pop-over arm.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@MainThread
public fun Activity.popOverOrTransformToActivity(
    @IdRes viewId: Int,
    cls: Class<*>,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(this),
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
): Boolean = popOverOrTransformToActivity(viewId, Intent(this, cls), transitionName, popOverOptions, duration, fadeMode)

/**
 * Opens [T] as a pop-over or container transform, with [viewId] as the transform origin; see the [Intent] overload.
 * @param duration The container transform duration in milliseconds; ignored on the pop-over arm.
 * @param fadeMode The container transform fade mode; ignored on the pop-over arm.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@NoCoverage
@MainThread
public inline fun <reified T : Activity> Activity.popOverOrTransformToActivity(
    @IdRes viewId: Int,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(this),
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
): Boolean = popOverOrTransformToActivity(viewId, Intent(this, T::class.java), transitionName, popOverOptions, duration, fadeMode)

/**
 * Opens [intent] as a gated launch.
 * The view identified by [viewId] is the container transform origin; the pop-over position comes from [popOverOptions].
 *
 * The pop-over condition of the [View] overload reads this activity's resources. Without a pop-over, the activity gets
 * the container transform of [transformToActivity], which launches without a transition if the view is not found.
 * @param duration The container transform duration in milliseconds; ignored on the pop-over arm.
 * @param fadeMode The container transform fade mode; ignored on the pop-over arm.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@MainThread
public fun Activity.popOverOrTransformToActivity(
    @IdRes viewId: Int,
    intent: Intent,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(this),
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
): Boolean =
    if (showsPopOver()) {
        launchPopOver(intent, popOverOptions)
    } else {
        transformToActivity(viewId, intent, transitionName, duration, fadeMode)
    }

private fun Activity.launchPopOver(
    intent: Intent,
    popOverOptions: PopOverOptions,
): Boolean = launchGated { startPopOverActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), popOverOptions) }

// One UI starts a pop-over only in a task whose smallest width is at least 600dp.
private fun Activity.showsPopOver(): Boolean = DeviceInfo.isOneUI() && DeviceLayoutUtil.isTabletLayout(resources)
