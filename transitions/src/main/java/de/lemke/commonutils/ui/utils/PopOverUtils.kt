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
import androidx.annotation.MainThread
import de.lemke.commonutils.transitions.NoCoverage
import dev.oneuiproject.oneui.ktx.startPopOverActivity
import dev.oneuiproject.oneui.popover.PopOverOptions

/**
 * Starts [cls] as a pop-over with [popOverOptions] and [Intent.FLAG_ACTIVITY_SINGLE_TOP]; see the [Intent] overload.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@MainThread
public fun Activity.singleLaunchPopOverActivity(
    cls: Class<*>,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(this),
): Boolean = singleLaunchPopOverActivity(Intent(this, cls), popOverOptions)

/**
 * Starts [T] as a pop-over with [popOverOptions] and [Intent.FLAG_ACTIVITY_SINGLE_TOP]; see the [Intent] overload.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@NoCoverage
@MainThread
public inline fun <reified T : Activity> Activity.singleLaunchPopOverActivity(
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(this),
): Boolean = singleLaunchPopOverActivity(Intent(this, T::class.java), popOverOptions)

/**
 * Starts [intent] as a pop-over with [popOverOptions] and [Intent.FLAG_ACTIVITY_SINGLE_TOP], as a gated launch.
 * One UI decides whether the screen shows it as a pop-over or full-screen; other devices start it as a normal activity.
 * [intent] stays unchanged.
 * @return true if the activity was started, false if the launch latch dropped it; see [singleLaunchActivity].
 */
@MainThread
public fun Activity.singleLaunchPopOverActivity(
    intent: Intent,
    popOverOptions: PopOverOptions = PopOverOptions.topCenterAnchored(this),
): Boolean =
    launchGated {
        startPopOverActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), popOverOptions)
    }
