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
import android.app.ActivityOptions
import android.content.Intent
import android.view.View
import androidx.annotation.IdRes
import de.lemke.commonutils.NoCoverage
import dev.oneuiproject.oneui.ktx.activity

private const val STATE_ANIMATOR_RESTORE_DELAY_MS = 1_000L

/**
 * Extension function to start an activity with a shared element transition from a view.
 * @receiver View The view to transition from.
 * @param cls The class of the activity to start.
 * @param transitionName The name of the shared element transition.
 * Transition names should be unique within the view hierarchy.
 * @param duration The duration of the transition in milliseconds.
 * @param fadeMode The fade mode for the transition.
 */
fun View.transformToActivity(
    cls: Class<*>,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) = transformToActivity(Intent(context, cls), transitionName, duration, fadeMode)

/**
 * Extension function to start an activity with a shared element transition from a view.
 * @receiver View The view to transition from.
 * @param transitionName The name of the shared element transition.
 * @param duration The duration of the transition in milliseconds.
 * @param fadeMode The fade mode for the transition.
 */
@NoCoverage
inline fun <reified T : Activity> View.transformToActivity(
    transitionName: String = DEFAULT_TRANSITION_NAME,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) = transformToActivity(Intent(context, T::class.java), transitionName, duration, fadeMode)

/**
 * Extension function to start an activity with a shared element transition from a view.
 * @receiver View The view to transition from.
 * @param intent The intent to start the new activity.
 * @param transitionName The name of the shared element transition.
 * Transition names should be unique within the view hierarchy.
 * @param duration The duration of the transition in milliseconds.
 * @param fadeMode The fade mode for the transition.
 */
fun View.transformToActivity(
    intent: Intent,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) {
    val activity =
        context.activity ?: run {
            context.startActivity(intent)
            return
        }
    suspendStateListAnimator()
    this.transitionName = transitionName
    val bundle = ActivityOptions.makeSceneTransitionAnimation(activity, this, transitionName).toBundle()
    intent
        .putExtra(TRANSITION_NAME_KEY, transitionName)
        .putExtra(DURATION_KEY, duration)
        .putExtra(FADE_MODE_KEY, fadeMode)
    context.startActivity(intent, bundle)
}

/**
 * Starts an activity with a shared element transition from the view identified by [viewId].
 * Falls back to a plain startActivity if the view is not found (e.g., recycled drawer item).
 * @receiver The activity that owns the view hierarchy.
 * @param viewId The ID of the view to transition from.
 * @param cls The class of the activity to start.
 * @param transitionName The name of the shared element transition.
 * @param duration The duration of the transition in milliseconds.
 * @param fadeMode The fade mode for the transition.
 */
fun Activity.transformToActivity(
    @IdRes viewId: Int,
    cls: Class<*>,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) = transformToActivity(viewId, Intent(this, cls), transitionName, duration, fadeMode)

/**
 * Starts an activity with a shared element transition from the view identified by [viewId].
 * Falls back to a plain startActivity if the view is not found.
 * @receiver The activity that owns the view hierarchy.
 * @param viewId The ID of the view to transition from.
 * @param transitionName The name of the shared element transition.
 * @param duration The duration of the transition in milliseconds.
 * @param fadeMode The fade mode for the transition.
 */
@NoCoverage
inline fun <reified T : Activity> Activity.transformToActivity(
    @IdRes viewId: Int,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) = transformToActivity(viewId, Intent(this, T::class.java), transitionName, duration, fadeMode)

/**
 * Starts an activity with a shared element transition from the view identified by [viewId].
 * Falls back to a plain startActivity if the view is not found (e.g., recycled drawer item).
 * @receiver The activity that owns the view hierarchy.
 * @param viewId The ID of the view to transition from.
 * @param intent The intent to start the new activity.
 * @param transitionName The name of the shared element transition.
 * @param duration The duration of the transition in milliseconds.
 * @param fadeMode The fade mode for the transition.
 */
fun Activity.transformToActivity(
    @IdRes viewId: Int,
    intent: Intent,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) {
    val view = findViewById<View>(viewId)
    if (view != null) {
        view.transformToActivity(intent, transitionName, duration, fadeMode)
    } else {
        startActivity(intent)
    }
}

/**
 * Starts an activity with a shared element transition from [view].
 * Falls back to a plain startActivity if [view] is null.
 * @receiver The activity to start from.
 * @param view The view to transition from, or null to fall back to a plain startActivity.
 * @param intent The intent to start the new activity.
 * @param transitionName The name of the shared element transition.
 * @param duration The duration of the transition in milliseconds.
 * @param fadeMode The fade mode for the transition.
 */
fun Activity.transformToActivity(
    view: View?,
    intent: Intent,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) {
    if (view != null) {
        view.transformToActivity(intent, transitionName, duration, fadeMode)
    } else {
        startActivity(intent)
    }
}

/**
 * Starts an activity with a shared element transition from [view].
 * Falls back to a plain startActivity if [view] is null.
 * @receiver The activity to start from.
 * @param view The view to transition from, or null to fall back to a plain startActivity.
 * @param transitionName The name of the shared element transition.
 * @param duration The duration of the transition in milliseconds.
 * @param fadeMode The fade mode for the transition.
 */
@NoCoverage
inline fun <reified T : Activity> Activity.transformToActivity(
    view: View?,
    transitionName: String = DEFAULT_TRANSITION_NAME,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) = transformToActivity(view, Intent(this, T::class.java), transitionName, duration, fadeMode)

/**
 * Workaround: Temporary disable item view's StateListAnimator
 * */
private fun View.suspendStateListAnimator() {
    val sla = stateListAnimator
    stateListAnimator = null
    postDelayed({ stateListAnimator = sla }, STATE_ANIMATOR_RESTORE_DELAY_MS)
}
