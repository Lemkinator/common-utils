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
package de.lemke.commonutils.ui.activity

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import de.lemke.commonutils.pinLottieAnimationsToFirstFrame
import de.lemke.commonutils.ui.utils.setupCommonUtilsAboutMeActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CommonUtilsAboutMeActivityScreenshotTest {
    @Before
    fun setUp() {
        // AppCompatDelegate.setDefaultNightMode() is a real static singleton, not a Robolectric
        // shadow - reset before each test so an earlier test's leftover mode can't change how the
        // "+night" qualifier renders here.
        setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        setupCommonUtilsAboutMeActivity()
    }

    @After
    fun tearDown() = setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    @Test
    fun aboutMeActivity_default() {
        ActivityScenario.launch(CommonUtilsAboutMeActivity::class.java).use { scenario ->
            scenario.onActivity { (it.window.decorView as ViewGroup).pinLottieAnimationsToFirstFrame() }
            onView(isRoot()).captureRoboImage("src/test/screenshots/about_me_default.png")
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun aboutMeActivity_default_dark() {
        ActivityScenario.launch(CommonUtilsAboutMeActivity::class.java).use { scenario ->
            scenario.onActivity { (it.window.decorView as ViewGroup).pinLottieAnimationsToFirstFrame() }
            onView(isRoot()).captureRoboImage("src/test/screenshots/about_me_default_dark.png")
        }
    }
}
