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

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.app.Activity
import android.app.Activity.OVERRIDE_TRANSITION_CLOSE
import android.app.Activity.OVERRIDE_TRANSITION_OPEN
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.transition.TransitionManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.transition.platform.MaterialArcMotion
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransform.FADE_MODE_CROSS
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback

private const val TAG = "TransformationUtils"
private const val TRANSITION_NAME_KEY = "commonUtilsTransitionNameKey"

@PublishedApi
internal const val DEFAULT_TRANSITION_NAME = "commonUtilsActivityTransitionName"
private const val DURATION_KEY = "commonUtilsDurationKey"

@PublishedApi
internal const val DEFAULT_DURATION = 400L
private const val STATE_ANIMATOR_RESTORE_DELAY_MS = 1_000L
private const val FADE_MODE_KEY = "commonUtilsFadeModeKey"

@PublishedApi
internal const val DEFAULT_FADE_MODE = FADE_MODE_CROSS

/**
 * Extension function to get a configured MaterialContainerTransform for the Activity.
 * @receiver The activity where the transition will be applied.
 * @return MaterialContainerTransform The configured container transform.
 */
fun Activity.getTransitionContainerTransform() =
    MaterialContainerTransform().apply {
        addTarget(android.R.id.content)
        pathMotion = MaterialArcMotion()
        duration = intent.getLongExtra(DURATION_KEY, DEFAULT_DURATION)
        fadeMode = intent.getIntExtra(FADE_MODE_KEY, DEFAULT_FADE_MODE)
    }

/**
 * Prepares the activity for a shared element transition from this activity.
 * Call in `onCreate`, after `super.onCreate`.
 * @receiver The activity to prepare.
 */
fun Activity.prepareActivityTransformationFrom() {
    window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
    setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
    window.sharedElementsUseOverlay = false
    val lifecycle =
        (this as? LifecycleOwner)?.lifecycle ?: run {
            Log.w(TAG, "Activity is not a LifecycleOwner; exit transition cleanup skipped.")
            return
        }
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (isFinishing) {
                    setExitSharedElementCallback(null)
                    window.sharedElementExitTransition = null
                    window.exitTransition = null
                }
            }
        },
    )
}

/**
 * Prepares the activity for a shared element transition to this activity.
 * Call in `onCreate`, after `super.onCreate`.
 * @receiver The activity to prepare.
 */
fun Activity.prepareActivityTransformationTo() {
    window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
    val transitionName =
        intent.getStringExtra(TRANSITION_NAME_KEY) ?: run {
            Log.w(TAG, "No transition name found. Skipping transformation.")
            return
        }
    ViewCompat.setTransitionName(findViewById(android.R.id.content), transitionName)
    setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
    window.sharedElementEnterTransition = getTransitionContainerTransform()
    window.sharedElementReturnTransition = getTransitionContainerTransform()
    val lifecycle =
        (this as? LifecycleOwner)?.lifecycle ?: run {
            Log.w(TAG, "Activity is not a LifecycleOwner; enter transition cleanup skipped.")
            return
        }
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (isFinishing) {
                    setEnterSharedElementCallback(null)
                    window.sharedElementEnterTransition = null
                    window.sharedElementReturnTransition = null
                }
            }
        },
    )
}

/**
 * Prepares the activity for a shared element transition both from and to this activity.
 * Call in `onCreate`, after `super.onCreate`.
 * @receiver The activity to prepare.
 */
fun Activity.prepareActivityTransformationBetween() {
    prepareActivityTransformationFrom()
    prepareActivityTransformationTo()
}

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
        context as? Activity ?: run {
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

/**
 * Creates a configured MaterialContainerTransform for a view transition.
 * @receiver View The view to transition from.
 * @param endView The view to transition to.
 * @param duration The duration of the transition in milliseconds.
 * @param fadeMode The fade mode for the transition.
 * @return MaterialContainerTransform The configured container transform.
 */
@VisibleForTesting
internal fun View.getContainerTransform(
    endView: View,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) = MaterialContainerTransform().apply {
    this.startView = this@getContainerTransform
    this.endView = endView
    this.duration = duration
    this.fadeMode = fadeMode
    scrimColor = TRANSPARENT
    pathMotion = MaterialArcMotion()
    addTarget(endView)
}

/**
 * Executes the container transform: hides this view, shows [targetView], and begins the transition.
 * Extracted from [transformTo]'s [View.post] lambda to allow direct testing without window attachment.
 */
@VisibleForTesting
internal fun View.performTransform(
    container: ViewGroup,
    targetView: View,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) {
    TransitionManager.endTransitions(container)
    isVisible = false
    targetView.isVisible = true
    TransitionManager.beginDelayedTransition(container, getContainerTransform(targetView, duration, fadeMode))
}

/**
 * Transitions from this view to the target view. Existing transition in the parent view will be stopped.
 * @receiver View The view to transition from.
 * @param targetView The view to transition to.
 * @param duration The duration of the transition in milliseconds. Default is 400L.
 * @param fadeMode The fade mode for the transition. Default is MaterialContainerTransform.FADE_MODE_CROSS.
 */
fun View.transformTo(
    targetView: View,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) {
    (parent as ViewGroup).also { container ->
        container.post { performTransform(container, targetView, duration, fadeMode) }
    }
}

/**
 * Applies a fade-in open transition for this activity.
 *
 * **Call in two places for full API coverage:**
 * - **Destination** `onCreate` (after `super.onCreate`) — required for API 34+;
 *   overridePendingTransition is a no-op there on pre-34.
 * - **Source** immediately after a bare `startActivity` (no finish) — required for pre-34.
 *   On API 34+, overrideActivityTransition works from both sides; if both source and destination
 *   call this, they agree on the same animations and the redundancy is harmless.
 *
 * When the source calls `startActivity` and then [finishWithFade], the source-side call here is
 * unnecessary — [finishWithFade] already applies the entering fade to the new activity on pre-34.
 */
fun Activity.overrideFadeOpenTransition() {
    if (SDK_INT >= UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(fade_in, fade_out)
    }
}

/** Applies a fade-out close transition and finishes the activity (predictive-back aware). */
fun Activity.finishWithFade() {
    if (SDK_INT >= UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, fade_in, fade_out)
        finishAfterTransition()
    } else {
        finishAfterTransition()
        @Suppress("DEPRECATION")
        overridePendingTransition(fade_in, fade_out)
    }
}
