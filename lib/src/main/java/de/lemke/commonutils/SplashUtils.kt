@file:Suppress("unused")

package de.lemke.commonutils

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.View.ALPHA
import android.view.View.SCALE_X
import android.view.View.SCALE_Y
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.KeepOnScreenCondition
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.System.currentTimeMillis

private const val TAG = "SplashUtils"

fun AppCompatActivity.configureCommonUtilsSplashScreen(splashScreen: SplashScreen, root: View, condition: KeepOnScreenCondition) {
    splashScreen.setKeepOnScreenCondition(condition)
    splashScreen.setOnExitAnimationListener { splash ->
        val splashAnimator: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(
            splash.view,
            PropertyValuesHolder.ofFloat(ALPHA, 0f),
            PropertyValuesHolder.ofFloat(SCALE_X, 1.2f),
            PropertyValuesHolder.ofFloat(SCALE_Y, 1.2f)
        )
        splashAnimator.interpolator = AccelerateDecelerateInterpolator()
        splashAnimator.duration = 400L
        splashAnimator.doOnEnd { splash.remove() }
        val contentAnimator: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(
            root,
            PropertyValuesHolder.ofFloat(ALPHA, 0f, 1f),
            PropertyValuesHolder.ofFloat(SCALE_X, 1.2f, 1f),
            PropertyValuesHolder.ofFloat(SCALE_Y, 1.2f, 1f)
        )
        contentAnimator.interpolator = AccelerateDecelerateInterpolator()
        contentAnimator.duration = 400L
        val remainingDuration =
            splash.iconAnimationDurationMillis - (currentTimeMillis() - splash.iconAnimationStartMillis).coerceAtLeast(0L)
        lifecycleScope.launch {
            delay(remainingDuration)
            splashAnimator.start()
            contentAnimator.start()
        }
    }
}

