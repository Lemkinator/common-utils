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
 * If the launching activity is itself a One UI pop-over, or runs on a One UI device with a smallest width of at least
 * 600dp, the activity starts as a pop-over with [popOverOptions], without a container transform or scene transition.
 * Inside a pop-over a transform would clip to the pop-over window, and its return transition would never run. A start from
 * a full-window screen adds [Intent.FLAG_ACTIVITY_SINGLE_TOP]; a start from inside a pop-over does not. Every other device,
 * phones and non-One UI large screens included, gets the container transform of [transformToActivity] with
 * [transitionName]. [intent] stays unchanged by the pop-over launch.
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
    val spec = ContainerTransformSpec(transitionName, duration, fadeMode)
    val activity = context.activity ?: return transformFrom(WindowKind.FullScreen, intent, spec)
    val windowKind = activity.windowKind()
    return if (activity.startsPopOver(windowKind)) {
        activity.launchPopOver(intent, windowKind, popOverOptions)
    } else {
        transformFrom(windowKind, intent, spec)
    }
}

/**
 * Opens [cls] as a pop-over or container transform, with [viewId] as the transform origin; see the [Intent] overload.
 * @param duration The container transform duration in milliseconds; ignored on the pop-over arm.
 * @param fadeMode The container transform fade mode; ignored on the pop-over arm.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@MainThread
@Suppress("LongParameterList")
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
 * The pop-over condition of the [View] overload reads this activity's resources, so it also holds when this activity
 * is itself a pop-over. Without a pop-over, the activity gets the container transform of [transformToActivity], which
 * launches without a transition if the view is not found.
 * @param duration The container transform duration in milliseconds; ignored on the pop-over arm.
 * @param fadeMode The container transform fade mode; ignored on the pop-over arm.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@MainThread
@Suppress("LongParameterList")
public fun Activity.popOverOrTransformToActivity(
    @IdRes viewId: Int,
    intent: Intent,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(this),
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
): Boolean {
    val windowKind = windowKind()
    return if (startsPopOver(windowKind)) {
        launchPopOver(intent, windowKind, popOverOptions)
    } else {
        transformFrom(windowKind, findViewById<View>(viewId), intent, ContainerTransformSpec(transitionName, duration, fadeMode))
    }
}

/**
 * Starts [intent] as a gated pop-over launch without a container transform and scene transition, so One UI animates the
 * open and the close. A page started from inside a pop-over lands in that pop-over.
 *
 * A launch from a [WindowKind.FullScreen] window is single-top. A launch from a [WindowKind.PopOver] window is not: the page
 * may open its own class with new extras and must get a new instance, and the launch latch already drops a double tap.
 * @param from The kind of this activity's window.
 */
internal fun Activity.launchPopOver(
    intent: Intent,
    from: WindowKind,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(this),
): Boolean =
    launchGated {
        val page =
            when (from) {
                WindowKind.FullScreen -> Intent(intent).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                WindowKind.PopOver -> Intent(intent)
            }
        startPopOverActivity(page, popOverOptions)
    }

// A pop-over has a smallest width of 360dp, so the width check alone misses a launch from inside one.
private fun Activity.startsPopOver(windowKind: WindowKind): Boolean = windowKind == WindowKind.PopOver || showsPopOver()

// One UI starts a pop-over only in a task whose smallest width is at least 600dp.
private fun Activity.showsPopOver(): Boolean = DeviceInfo.isOneUI() && DeviceLayoutUtil.isTabletLayout(resources)
