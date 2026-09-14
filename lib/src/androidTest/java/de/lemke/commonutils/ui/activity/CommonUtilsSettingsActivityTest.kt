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
import de.lemke.commonutils.R
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.ui.utils.setupCommonUtilsSettingsActivity
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric can't lay out the real ListView `addShareAppAndRateRelativeLinksCard()` needs
 * (the screenshot test stubs it out) - this runs the settings screen on a real device with
 * every card, including that one, wired up for real.
 */
@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class CommonUtilsSettingsActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val fakeSettings: SettingsRepository = SettingsRepository(freshTestPreferences())

    @Before
    fun setUp() {
        hiltRule.inject()
        setupCommonUtilsSettingsActivity(
            R.xml.preferences_design,
            R.xml.preferences_general_language_and_image_save_location,
            R.xml.preferences_dev_options_delete_app_data,
            R.xml.preferences_more_info,
        )
    }

    @Test
    fun activityLaunchesWithoutCrash() {
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use { scenario ->
            scenario.state shouldBe Lifecycle.State.RESUMED
        }
    }
}
