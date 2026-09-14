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

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.ui.utils.setupCommonUtilsAboutActivity
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric can't call Play Core's real `AppUpdateManagerFactory.create()` (the screenshot
 * test mocks it) - this runs the About screen on a real device against the real Play Core client.
 */
@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class CommonUtilsAboutActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val fakeSettings: SettingsRepository = SettingsRepository(freshTestPreferences())

    @Before
    fun setUp() {
        hiltRule.inject()
        setupCommonUtilsAboutActivity("1.0.0")
    }

    @After
    fun tearDown() {
        CommonUtilsAboutActivity.appVersion = ""
    }

    @Test
    fun activityLaunchesWithoutCrash() {
        ActivityScenario.launch(CommonUtilsAboutActivity::class.java).use { scenario ->
            scenario.state shouldBe Lifecycle.State.RESUMED
        }
    }
}
