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

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkInfo
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.ui.utils.setupCommonUtilsAboutActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * JUnit4 (not JUnit5/Kotest like the rest of this module): `HiltAndroidRule`/`@HiltAndroidTest`
 * require a JUnit4 `@RunWith(RobolectricTestRunner::class)` runner, so this class runs under the
 * `junit-vintage-engine` island — see `lib/build.gradle.kts` test dependencies.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CommonUtilsAboutActivityScreenshotTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val fakeSettings: SettingsRepository = SettingsRepository(freshTestPreferences())

    @Before
    fun setUp() {
        // AppCompatDelegate.setDefaultNightMode() is a real static singleton, not a Robolectric
        // shadow - reset before each test so an earlier test's leftover mode can't override how
        // the "+night" qualifier renders here (a non-FOLLOW_SYSTEM mode pins day/night regardless
        // of the resource qualifier).
        setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        hiltRule.inject()
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Robolectric reports no active network by default, which the activity reads as
        // NoConnection - mark the active network validated so checkUpdate() proceeds to the
        // (faked) update check instead of short-circuiting into the offline state.
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        shadowOf(connectivityManager).apply {
            setActiveNetworkInfo(
                ShadowNetworkInfo.newInstance(
                    NetworkInfo.DetailedState.CONNECTED,
                    ConnectivityManager.TYPE_WIFI,
                    0,
                    true,
                    NetworkInfo.State.CONNECTED,
                ),
            )
            // NetworkCapabilities has no public builder in the compile-time SDK stub (its Builder
            // is a system/test API) - a relaxed mock stands in for the "validated" capability the
            // activity actually reads.
            val networkCapabilities =
                mockk<NetworkCapabilities>(relaxed = true) { every { hasCapability(NET_CAPABILITY_VALIDATED) } returns true }
            setNetworkCapabilities(connectivityManager.activeNetwork, networkCapabilities)
        }
        mockkStatic(AppUpdateManagerFactory::class)
        every { AppUpdateManagerFactory.create(any()) } returns FakeAppUpdateManager(context).apply { setUpdateNotAvailable() }
        setupCommonUtilsAboutActivity("1.0.0")
    }

    @After
    fun tearDown() {
        unmockkAll()
        setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        CommonUtilsAboutActivity.appVersion = ""
    }

    @Test
    fun aboutActivity_default() {
        ActivityScenario.launch(CommonUtilsAboutActivity::class.java).use {
            shadowOf(Looper.getMainLooper()).idle()
            onView(isRoot()).captureRoboImage("src/test/screenshots/about_default.png")
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun aboutActivity_default_dark() {
        ActivityScenario.launch(CommonUtilsAboutActivity::class.java).use {
            shadowOf(Looper.getMainLooper()).idle()
            onView(isRoot()).captureRoboImage("src/test/screenshots/about_default_dark.png")
        }
    }
}
