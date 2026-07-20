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

import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen
import de.lemke.commonutils.ui.utils.configureCommonUtilsSplashScreen
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SplashUtilsRobolectricTest {
    private fun setupActivity(): AppCompatActivity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()

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
}
