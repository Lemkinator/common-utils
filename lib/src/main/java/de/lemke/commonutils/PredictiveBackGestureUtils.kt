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
package de.lemke.commonutils

import android.annotation.SuppressLint
import android.graphics.Outline
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
import androidx.activity.BackEventCompat
import androidx.activity.BackEventCompat.Companion.EDGE_LEFT
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.R.color.sesl_round_and_bgcolor_dark
import androidx.appcompat.R.color.sesl_round_and_bgcolor_light
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.util.SeslMisc.isLightTheme
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Registers [onBackPressedLogic] as the back handler, enabled whenever [backPressLogicEnabled] emits `true`. */
@NoCoverage
inline fun Fragment.addOnBackLogic(
    backPressLogicEnabled: StateFlow<Boolean>,
    crossinline onBackPressedLogic: () -> Unit = {},
) {
    if (SDK_INT >= TIRAMISU) {
        val onBackInvokedCallback = OnBackInvokedCallback { onBackPressedLogic.invoke() }
        lifecycleScope.launch {
            try {
                backPressLogicEnabled
                    .flowWithLifecycle(lifecycle)
                    .distinctUntilChanged()
                    .collectLatest { register ->
                        if (register) {
                            requireActivity().onBackInvokedDispatcher.registerOnBackInvokedCallback(PRIORITY_DEFAULT, onBackInvokedCallback)
                        } else {
                            requireActivity().onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCallback)
                        }
                    }
            } finally {
                activity?.onBackInvokedDispatcher?.unregisterOnBackInvokedCallback(onBackInvokedCallback)
            }
        }
    } else {
        val onBackPressedCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    onBackPressedLogic.invoke()
                }
            }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        lifecycleScope.launch {
            backPressLogicEnabled.flowWithLifecycle(lifecycle).collectLatest { enable -> onBackPressedCallback.isEnabled = enable }
        }
    }
}

/** Path interpolator matching the predictive-back motion spec deceleration curve. */
val GestureInterpolator = PathInterpolatorCompat.create(0f, 0f, 0f, 1f)

/** [ViewOutlineProvider] that applies rounded corners scaled with back-gesture progress. */
class BackAnimationOutlineProvider : ViewOutlineProvider() {
    /** Current corner radius in pixels, driven by [progress]. */
    var radius = 0f

    /** Gesture progress in [0, 1]; setting it updates [radius] proportionally. */
    var progress: Float = 0f
        set(value) {
            field = value
            radius = value * 100f
        }

    /** Applies a rounded-rectangle outline scaled to the current [radius]. */
    override fun getOutline(
        view: View,
        outline: Outline,
    ) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}

/** Adds a predictive-back animation to [animatedView] that scales and shifts it as the user swipes back, with optional in-app review. */
fun AppCompatActivity.setCustomBackAnimation(
    animatedView: View,
    backEnabled: StateFlow<Boolean>? = null,
    showInAppReviewIfPossible: Boolean = false,
) {
    setWindowTransparent(true)
    val predictiveBackMargin = resources.getDimension(R.dimen.predictive_back_margin)
    var initialTouchY = -1f
    val outlineProvider = BackAnimationOutlineProvider()
    animatedView.clipToOutline = true
    animatedView.outlineProvider = outlineProvider
    val showInAppReview = showInAppReviewIfPossible && canShowInAppReview()
    val callback =
        object : OnBackPressedCallback(backEnabled?.value != false) {
            override fun handleOnBackPressed() {
                if (showInAppReview) {
                    showInAppReviewOrFinish()
                } else {
                    finishAfterTransition()
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (showInAppReview) return
                val progress = GestureInterpolator.getInterpolation(backEvent.progress)
                if (initialTouchY < 0f) initialTouchY = backEvent.touchY
                val progressY = GestureInterpolator.getInterpolation((backEvent.touchY - initialTouchY) / animatedView.height)

                // See the motion spec about the calculations below.
                // https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back#motion-specs

                // Shift horizontally.
                val maxTranslationX = (animatedView.width / 20) - predictiveBackMargin
                animatedView.translationX = progress * maxTranslationX * if (backEvent.swipeEdge == EDGE_LEFT) 1 else -1

                // Shift vertically.
                val maxTranslationY = (animatedView.height / 20) - predictiveBackMargin
                animatedView.translationY = progressY * maxTranslationY

                // Scale down from 100% to 90%.
                val scale = 1f - (0.1f * progress)
                animatedView.scaleX = scale
                animatedView.scaleY = scale

                // apply rounded corners
                outlineProvider.progress = progress
                animatedView.invalidateOutline()
            }

            override fun handleOnBackCancelled() {
                initialTouchY = -1f
                animatedView.run {
                    translationX = 0f
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                }
            }
        }
    onBackPressedDispatcher.addCallback(this, callback)
    backEnabled?.apply { lifecycleScope.launch { flowWithLifecycle(lifecycle).collectLatest { enable -> callback.isEnabled = enable } } }
}

/** Makes the activity window transparent or restores its default background, required for the predictive-back animation. */
fun AppCompatActivity.setWindowTransparent(transparent: Boolean) {
    window.apply {
        if (transparent) {
            clearFlags(FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.commonutils_transparent_window_bg_color)
            if (SDK_INT >= Build.VERSION_CODES.R) setTranslucent(true)
        } else {
            addFlags(FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(defaultWindowBackground)
            if (SDK_INT >= Build.VERSION_CODES.R) setTranslucent(false)
        }
    }
}

/** The theme-appropriate default window background color for this activity. */
val AppCompatActivity.defaultWindowBackground: Int
    @NoCoverage
    @SuppressLint("RestrictedApi", "PrivateResource")
    get() = if (isLightTheme(this)) sesl_round_and_bgcolor_light else sesl_round_and_bgcolor_dark
