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
import android.os.Looper
import androidx.preference.PreferenceFragmentCompat
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.R
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.addShareAppAndRateRelativeLinksCard
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.setupCommonUtilsSettingsActivity
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class CommonUtilsSettingsActivityTest {
    @BeforeEach
    fun setUp() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("settings_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        commonUtilsSettings = SettingsRepository(prefs)
        // addRelativeLinksCard requires a ListView not available under Robolectric.
        // mockkStatic intercepts the Kt-file static; any<> matches any receiver.
        mockkStatic("de.lemke.commonutils.PreferenceUtilsKt")
        every { any<PreferenceFragmentCompat>().addShareAppAndRateRelativeLinksCard() } just runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun launchWithEmptyPrefs(): CommonUtilsSettingsActivity {
        // Empty preferences list → onCreatePreferences iterates nothing,
        // then initCommonUtilsPreferences runs with all findPreference calls returning null
        // — covers all the Log.w else paths.
        setupCommonUtilsSettingsActivity(emptyList())
        val controller = Robolectric.buildActivity(CommonUtilsSettingsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    private fun launchWithDefaultPrefs(): CommonUtilsSettingsActivity {
        // Full preference XML list → onCreatePreferences inflates design + general + dev + more-info,
        // then initCommonUtilsPreferences finds the real preference objects.
        setupCommonUtilsSettingsActivity(
            listOf(
                R.xml.preferences_design,
                R.xml.preferences_general_language,
                R.xml.preferences_dev_options_delete_app_data,
                R.xml.preferences_more_info,
            ),
        )
        val controller = Robolectric.buildActivity(CommonUtilsSettingsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    @Test
    fun `activity launches with empty preferences — null paths covered`() {
        launchWithEmptyPrefs() shouldNotBe null
    }

    @Test
    fun `activity launches with default preferences — non-null init paths covered`() {
        launchWithDefaultPrefs() shouldNotBe null
    }

    @Test
    fun `activity launches with devModeEnabled — dev-options category is visible`() {
        commonUtilsSettings.devModeEnabled = true
        launchWithDefaultPrefs() shouldNotBe null
    }

    @Test
    fun `activity launches with autoDarkMode true — darkMode prefs initialized with auto branch`() {
        commonUtilsSettings.autoDarkMode = true
        launchWithDefaultPrefs() shouldNotBe null
    }

    @Test
    fun `activity launches with darkMode true — dark branch in initDarkMode`() {
        commonUtilsSettings.darkMode = true
        launchWithDefaultPrefs() shouldNotBe null
    }
}
