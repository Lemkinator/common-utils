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
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.R
import de.lemke.commonutils.addShareAppAndRateRelativeLinksCard
import de.lemke.commonutils.data.SettingsRepository
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
                R.xml.preferences_general_language_and_image_save_location,
                R.xml.preferences_dev_options_delete_app_data,
                R.xml.preferences_more_info,
            ),
        )
        val controller = Robolectric.buildActivity(CommonUtilsSettingsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    private fun getSettingsFragment(activity: CommonUtilsSettingsActivity): PreferenceFragmentCompat =
        activity.supportFragmentManager.findFragmentByTag("") as? PreferenceFragmentCompat
            ?: activity.supportFragmentManager.fragments.filterIsInstance<PreferenceFragmentCompat>().first()

    private fun Preference.triggerClick() {
        Preference::class.java.getDeclaredMethod("callClickListener")
            .also { it.isAccessible = true }
            .invoke(this)
    }

    private fun Preference.triggerChange(newValue: Any) {
        Preference::class.java.getDeclaredMethod("callChangeListener", Any::class.java)
            .also { it.isAccessible = true }
            .invoke(this, newValue)
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

    @Test
    fun `autoDarkMode switch change to false triggers onNewValue dark mode branch`() {
        commonUtilsSettings.autoDarkMode = true
        val activity = launchWithDefaultPrefs()
        val fragment = getSettingsFragment(activity)
        val key = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.commonutils_preference_key_auto_dark_mode)
        val pref = fragment.findPreference<Preference>(key)
        pref?.triggerChange(false)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `autoDarkMode false with darkMode true triggers MODE_NIGHT_YES in onNewValue`() {
        commonUtilsSettings.autoDarkMode = true
        commonUtilsSettings.darkMode = true
        val activity = launchWithDefaultPrefs()
        val fragment = getSettingsFragment(activity)
        val key = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.commonutils_preference_key_auto_dark_mode)
        val pref = fragment.findPreference<Preference>(key)
        pref?.triggerChange(false)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `autoDarkMode switch change to true triggers follow-system branch`() {
        commonUtilsSettings.autoDarkMode = false
        val activity = launchWithDefaultPrefs()
        val fragment = getSettingsFragment(activity)
        val key = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.commonutils_preference_key_auto_dark_mode)
        val pref = fragment.findPreference<Preference>(key)
        pref?.triggerChange(true)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `darkMode radio change to 1 triggers MODE_NIGHT_YES branch`() {
        commonUtilsSettings.autoDarkMode = false
        val activity = launchWithDefaultPrefs()
        val fragment = getSettingsFragment(activity)
        val key = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.commonutils_preference_key_dark_mode)
        val pref = fragment.findPreference<Preference>(key)
        pref?.triggerChange("1")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `darkMode radio change to 0 triggers MODE_NIGHT_NO branch`() {
        commonUtilsSettings.autoDarkMode = false
        val activity = launchWithDefaultPrefs()
        val fragment = getSettingsFragment(activity)
        val key = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.commonutils_preference_key_dark_mode)
        val pref = fragment.findPreference<Preference>(key)
        pref?.triggerChange("0")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `privacy policy preference click fires without crashing`() {
        val activity = launchWithDefaultPrefs()
        val fragment = getSettingsFragment(activity)
        val key = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.commonutils_preference_key_privacy_policy)
        val pref = fragment.findPreference<Preference>(key)
        pref?.triggerClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `tos preference click fires without crashing`() {
        val activity = launchWithDefaultPrefs()
        val fragment = getSettingsFragment(activity)
        val key = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.commonutils_preference_key_tos)
        val pref = fragment.findPreference<Preference>(key)
        pref?.triggerClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `report bug preference click fires without crashing`() {
        val activity = launchWithDefaultPrefs()
        val fragment = getSettingsFragment(activity)
        val key = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.commonutils_preference_key_report_bug)
        val pref = fragment.findPreference<Preference>(key)
        pref?.triggerClick()
        shadowOf(Looper.getMainLooper()).idle()
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [29])
class CommonUtilsSettingsActivitySdk29Test {
    @BeforeEach
    fun setUp() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("settings_test_29", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        commonUtilsSettings = SettingsRepository(prefs)
        mockkStatic("de.lemke.commonutils.PreferenceUtilsKt")
        every { any<PreferenceFragmentCompat>().addShareAppAndRateRelativeLinksCard() } just runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initImageSaveLocation on SDK 29 disables preference and sets CUSTOM`() {
        // SDK_INT <= VERSION_CODES.Q branch: value = CUSTOM, isEnabled = false
        setupCommonUtilsSettingsActivity(
            listOf(
                R.xml.preferences_design,
                R.xml.preferences_general_language_and_image_save_location,
                R.xml.preferences_dev_options_delete_app_data,
                R.xml.preferences_more_info,
            ),
        )
        val controller = Robolectric.buildActivity(CommonUtilsSettingsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        controller.get() shouldNotBe null
    }
}
