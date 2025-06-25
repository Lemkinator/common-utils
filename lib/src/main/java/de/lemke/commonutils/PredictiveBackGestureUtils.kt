@file:Suppress("unused")

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
import kotlinx.coroutines.launch

private const val tag = "PredictiveBackGestureUtils"

inline fun Fragment.addOnBackLogic(
    backPressLogicEnabled: StateFlow<Boolean>,
    crossinline onBackPressedLogic: () -> Unit = {},
) {
    if (SDK_INT >= TIRAMISU) {
        val onBackInvokedCallback = OnBackInvokedCallback { onBackPressedLogic.invoke() }
        requireActivity().onBackInvokedDispatcher.registerOnBackInvokedCallback(PRIORITY_DEFAULT, onBackInvokedCallback)
        lifecycleScope.launch {
            backPressLogicEnabled
                .flowWithLifecycle(lifecycle)
                .collectLatest { register ->
                    if (register) {
                        requireActivity().onBackInvokedDispatcher.registerOnBackInvokedCallback(PRIORITY_DEFAULT, onBackInvokedCallback)
                    } else {
                        requireActivity().onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCallback)
                    }
                }
        }
    } else {
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
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

val GestureInterpolator = PathInterpolatorCompat.create(0f, 0f, 0f, 1f)

class BackAnimationOutlineProvider() : ViewOutlineProvider() {
    var radius = 0f
    var progress: Float = 0f
        set(value) {
            radius = value * 100f
        }

    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}

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
    val callback = object : OnBackPressedCallback(backEnabled?.value != false) {
        override fun handleOnBackPressed() {
            if (showInAppReview) showInAppReviewOrFinish()
            else finishAfterTransition()
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
            animatedView.scaleX = scale; animatedView.scaleY = scale

            // apply rounded corners
            outlineProvider.progress = progress
            animatedView.invalidateOutline()
        }

        override fun handleOnBackCancelled() {
            initialTouchY = -1f; animatedView.run { translationX = 0f; translationY = 0f; scaleX = 1f; scaleY = 1f }
        }
    }
    onBackPressedDispatcher.addCallback(this, callback)
    backEnabled?.apply { lifecycleScope.launch { flowWithLifecycle(lifecycle).collectLatest { enable -> callback.isEnabled = enable } } }
}

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

val AppCompatActivity.defaultWindowBackground: Int
    @SuppressLint("RestrictedApi", "PrivateResource")
    get() = if (isLightTheme(this)) sesl_round_and_bgcolor_light else sesl_round_and_bgcolor_dark
