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

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import de.lemke.commonutils.ui.utils.configureCommonUtilsSplashScreen
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SplashUtilsRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private fun setupActivity(): AppCompatActivity =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .setup()
            .track(destroyActivities)
            .get()

    @Test
    fun `configureCommonUtilsSplashScreen with null condition skips setKeepOnScreenCondition`() {
        val activity = setupActivity()
        val splashScreen = mockk<SplashScreen>(relaxed = true)
        val root: View = FrameLayout(activity)
        activity.configureCommonUtilsSplashScreen(splashScreen, root)
        verify(exactly = 0) { splashScreen.setKeepOnScreenCondition(any()) }
        verify(exactly = 1) { splashScreen.setOnExitAnimationListener(any()) }
    }

    @Test
    fun `configureCommonUtilsSplashScreen with condition calls setKeepOnScreenCondition`() {
        val activity = setupActivity()
        val splashScreen = mockk<SplashScreen>(relaxed = true)
        val root: View = FrameLayout(activity)
        activity.configureCommonUtilsSplashScreen(splashScreen, root) { false }
        verify(exactly = 1) { splashScreen.setKeepOnScreenCondition(any()) }
        verify(exactly = 1) { splashScreen.setOnExitAnimationListener(any()) }
    }

    @Test
    fun `exit animation fades the splash out, fades the content in and removes the splash`() {
        val activity = setupActivity()
        val splashScreen = mockk<SplashScreen>(relaxed = true)
        val listener = slot<SplashScreen.OnExitAnimationListener>()
        every { splashScreen.setOnExitAnimationListener(capture(listener)) } returns Unit
        val root: View = FrameLayout(activity).apply { alpha = 0f }
        val splashView: View = FrameLayout(activity)
        val provider =
            mockk<SplashScreenViewProvider>(relaxed = true) {
                every { view } returns splashView
                every { iconAnimationStartMillis } returns System.currentTimeMillis()
                every { iconAnimationDurationMillis } returns 200L
            }
        activity.configureCommonUtilsSplashScreen(splashScreen, root)

        listener.captured.onSplashScreenExit(provider)
        verify(exactly = 0) { provider.remove() }
        shadowOf(Looper.getMainLooper()).idleFor(1500.milliseconds.toJavaDuration())

        splashView.alpha shouldBe 0f
        root.alpha shouldBe 1f
        root.scaleX shouldBe 1f
        verify(exactly = 1) { provider.remove() }
    }
}
