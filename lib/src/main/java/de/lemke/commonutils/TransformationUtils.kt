@file:Suppress("unused")

package de.lemke.commonutils

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.util.Log
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import com.google.android.material.transition.platform.MaterialArcMotion
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import dev.oneuiproject.oneui.ktx.activity

private const val TAG = "TransformationUtils"
private const val TRANSITION_NAME_KEY = "commonUtilsTransitionNameKey"
private const val DEFAULT_TRANSITION_NAME = "commonUtilsActivityTransitionName"
private const val DURATION_KEY = "commonUtilsDurationKey"
private const val DEFAULT_DURATION = 400L
private const val FADE_MODE_KEY = "commonUtilsFadeModeKey"
private const val DEFAULT_FADE_MODE = MaterialContainerTransform.FADE_MODE_CROSS

/**
 * Extension function to get a configured MaterialContainerTransform for the Activity.
 * @receiver Activity The activity where the transition will be applied.
 * @return MaterialContainerTransform The configured container transform.
 */
fun Activity.getTransitionContainerTransform() = MaterialContainerTransform().apply {
    addTarget(android.R.id.content)
    pathMotion = MaterialArcMotion()
    duration = intent.getLongExtra(DURATION_KEY, DEFAULT_DURATION)
    fadeMode = intent.getIntExtra(FADE_MODE_KEY, DEFAULT_FADE_MODE)
}

/**
 * Prepares the activity for a shared element transition from this activity.
 * This should be called in the source activity before onCreate.
 * @receiver Activity The activity to prepare.
 */
fun Activity.prepareActivityTransformationFrom() {
    window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
    setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
    window.sharedElementsUseOverlay = false
}

/**
 * Prepares the activity for a shared element transition to this activity.
 * This should be called in the destination activity before onCreate.
 * @receiver Activity The activity to prepare.
 */
fun Activity.prepareActivityTransformationTo() {
    window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
    intent.getStringExtra(TRANSITION_NAME_KEY).let {
        if (it != null) {
            ViewCompat.setTransitionName(findViewById(android.R.id.content), it)
            setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
            window.sharedElementEnterTransition = getTransitionContainerTransform()
            window.sharedElementReturnTransition = getTransitionContainerTransform()
        } else {
            Log.w(TAG, "No transition name found. Skipping transformation.")
        }
    }
}

/**
 * Prepares the activity for a shared element transition both from and to this activity.
 * This should be called before onCreate.
 * @receiver Activity The activity to prepare.
 */
fun Activity.prepareActivityTransformationBetween() {
    prepareActivityTransformationFrom()
    prepareActivityTransformationTo()
}

/**
 * Extension function to start an activity with a shared element transition from a view.
 * @receiver View The view to transition from.
 * @param intent The intent to start the new activity.
 * @param transitionName The name of the shared element transition. Default is "activityTransitionName".
 * Transition names should be unique within the view hierarchy.
 * @param duration The duration of the transition in milliseconds. Default is 400L.
 * @param fadeMode The fade mode for the transition. Default is MaterialContainerTransform.FADE_MODE_CROSS.
 */
fun View.transformToActivity(
    intent: Intent,
    transitionName: String = DEFAULT_TRANSITION_NAME, //transitionNames should be unique within the view hierarchy
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE
) {
    this.transitionName = transitionName
    val bundle = ActivityOptions.makeSceneTransitionAnimation(context.activity, this, transitionName).toBundle()
    intent.putExtra(TRANSITION_NAME_KEY, transitionName)
        .putExtra(DURATION_KEY, duration)
        .putExtra(FADE_MODE_KEY, fadeMode)
    context.startActivity(intent, bundle)
}