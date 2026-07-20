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
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.DrainMainLooperRule
import de.lemke.commonutils.R
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.ui.utils.addShareAppAndRateRelativeLinksCard
import de.lemke.commonutils.ui.utils.setupCommonUtilsSettingsActivity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JUnit4 (not JUnit5/Kotest like the rest of this module): `HiltAndroidRule`/`@HiltAndroidTest`
 * require a JUnit4 `@RunWith(RobolectricTestRunner::class)` runner, so this class runs under the
 * `junit-vintage-engine` island — see `lib/build.gradle.kts` test dependencies.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class CommonUtilsSettingsActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val drainMainLooper = DrainMainLooperRule()

    @BindValue
    @JvmField
    val fakeSettings: SettingsRepository = SettingsRepository(freshTestPreferences("settings_test"))

    @Before
    fun setUp() {
        hiltRule.inject()
        // addRelativeLinksCard requires a ListView not available under Robolectric.
        // mockkStatic intercepts the Kt-file static; any<> matches any receiver.
        mockkStatic("de.lemke.commonutils.ui.utils.PreferenceUtilsKt")
        every { any<PreferenceFragmentCompat>().addShareAppAndRateRelativeLinksCard() } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
        // AppCompatDelegate.setDefaultNightMode() is a real static singleton, not a Robolectric
        // shadow — the dark-mode tests below leave it set to whatever they last triggered, which
        // would otherwise leak into whichever test runs next.
        setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    private fun launchWithEmptyPrefs(block: (CommonUtilsSettingsActivity) -> Unit) {
        // Empty preferences list → onCreatePreferences iterates nothing,
        // then initCommonUtilsPreferences runs with all findPreference calls returning null
        // - covers all the Log.w else paths.
        setupCommonUtilsSettingsActivity(emptyList())
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use { it.onActivity(block) }
    }

    private fun launchWithDefaultPrefs(block: (CommonUtilsSettingsActivity) -> Unit) {
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
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use { it.onActivity(block) }
    }

    private fun getSettingsFragment(activity: CommonUtilsSettingsActivity): PreferenceFragmentCompat =
        activity.supportFragmentManager.findFragmentByTag("") as? PreferenceFragmentCompat
            ?: activity.supportFragmentManager.fragments
                .filterIsInstance<PreferenceFragmentCompat>()
                .first()

    private fun Preference.triggerClick() {
        Preference::class.java
            .getDeclaredMethod("callClickListener")
            .also { it.isAccessible = true }
            .invoke(this)
    }

    private fun Preference.triggerChange(newValue: Any) {
        Preference::class.java
            .getDeclaredMethod("callChangeListener", Any::class.java)
            .also { it.isAccessible = true }
            .invoke(this, newValue)
    }

    @Test
    fun `activity launches with empty preferences - null paths covered`() {
        launchWithEmptyPrefs { activity -> activity shouldNotBe null }
    }

    @Test
    fun `activity launches with default preferences - non-null init paths covered`() {
        launchWithDefaultPrefs { activity -> activity shouldNotBe null }
    }

    @Test
    fun `activity launches with devModeEnabled - dev-options category is visible`() {
        fakeSettings.devModeEnabled = true
        launchWithDefaultPrefs { activity -> activity shouldNotBe null }
    }

    @Test
    fun `activity launches with autoDarkMode true - darkMode prefs initialized with auto branch`() {
        fakeSettings.autoDarkMode = true
        launchWithDefaultPrefs { activity -> activity shouldNotBe null }
    }

    @Test
    fun `activity launches with darkMode true - dark branch in initDarkMode`() {
        fakeSettings.darkMode = true
        launchWithDefaultPrefs { activity -> activity shouldNotBe null }
    }

    @Test
    fun `autoDarkMode switch change to false triggers onNewValue dark mode branch`() {
        fakeSettings.autoDarkMode = true
        launchWithDefaultPrefs { activity ->
            val fragment = getSettingsFragment(activity)
            val key =
                ApplicationProvider
                    .getApplicationContext<Context>()
                    .getString(R.string.commonutils_preference_key_auto_dark_mode)
            val pref = fragment.findPreference<Preference>(key)
            pref?.triggerChange(false)
        }
    }

    @Test
    fun `autoDarkMode false with darkMode true triggers MODE_NIGHT_YES in onNewValue`() {
        fakeSettings.autoDarkMode = true
        fakeSettings.darkMode = true
        launchWithDefaultPrefs { activity ->
            val fragment = getSettingsFragment(activity)
            val key =
                ApplicationProvider
                    .getApplicationContext<Context>()
                    .getString(R.string.commonutils_preference_key_auto_dark_mode)
            val pref = fragment.findPreference<Preference>(key)
            pref?.triggerChange(false)
        }
    }

    @Test
    fun `autoDarkMode switch change to true triggers follow-system branch`() {
        fakeSettings.autoDarkMode = false
        launchWithDefaultPrefs { activity ->
            val fragment = getSettingsFragment(activity)
            val key =
                ApplicationProvider
                    .getApplicationContext<Context>()
                    .getString(R.string.commonutils_preference_key_auto_dark_mode)
            val pref = fragment.findPreference<Preference>(key)
            pref?.triggerChange(true)
        }
    }

    @Test
    fun `darkMode radio change to 1 triggers MODE_NIGHT_YES branch`() {
        fakeSettings.autoDarkMode = false
        launchWithDefaultPrefs { activity ->
            val fragment = getSettingsFragment(activity)
            val key =
                ApplicationProvider
                    .getApplicationContext<Context>()
                    .getString(R.string.commonutils_preference_key_dark_mode)
            val pref = fragment.findPreference<Preference>(key)
            pref?.triggerChange("1")
        }
    }

    @Test
    fun `darkMode radio change to 0 triggers MODE_NIGHT_NO branch`() {
        fakeSettings.autoDarkMode = false
        launchWithDefaultPrefs { activity ->
            val fragment = getSettingsFragment(activity)
            val key =
                ApplicationProvider
                    .getApplicationContext<Context>()
                    .getString(R.string.commonutils_preference_key_dark_mode)
            val pref = fragment.findPreference<Preference>(key)
            pref?.triggerChange("0")
        }
    }

    @Test
    fun `privacy policy preference click fires without crashing`() {
        launchWithDefaultPrefs { activity ->
            val fragment = getSettingsFragment(activity)
            val key =
                ApplicationProvider
                    .getApplicationContext<Context>()
                    .getString(R.string.commonutils_preference_key_privacy_policy)
            val pref = fragment.findPreference<Preference>(key)
            pref?.triggerClick()
        }
    }

    @Test
    fun `tos preference click fires without crashing`() {
        launchWithDefaultPrefs { activity ->
            val fragment = getSettingsFragment(activity)
            val key =
                ApplicationProvider
                    .getApplicationContext<Context>()
                    .getString(R.string.commonutils_preference_key_tos)
            val pref = fragment.findPreference<Preference>(key)
            pref?.triggerClick()
        }
    }

    @Test
    fun `report bug preference click fires without crashing`() {
        launchWithDefaultPrefs { activity ->
            val fragment = getSettingsFragment(activity)
            val key =
                ApplicationProvider
                    .getApplicationContext<Context>()
                    .getString(R.string.commonutils_preference_key_report_bug)
            val pref = fragment.findPreference<Preference>(key)
            pref?.triggerClick()
        }
    }
}

/**
 * JUnit4 (not JUnit5/Kotest like the rest of this module) — see [CommonUtilsSettingsActivityTest]'s
 * class doc for why.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [29])
class CommonUtilsSettingsActivitySdk29Test {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val fakeSettings: SettingsRepository = SettingsRepository(freshTestPreferences("settings_test_29"))

    @Before
    fun setUp() {
        hiltRule.inject()
        mockkStatic("de.lemke.commonutils.ui.utils.PreferenceUtilsKt")
        every { any<PreferenceFragmentCompat>().addShareAppAndRateRelativeLinksCard() } just runs
    }

    @After
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
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<PreferenceFragmentCompat>()
                        .first()
                val key =
                    ApplicationProvider.getApplicationContext<Context>().getString(R.string.commonutils_preference_key_image_save_location)
                val pref = fragment.findPreference<DropDownPreference>(key)
                pref shouldNotBe null
                pref!!.isEnabled shouldBe false
                pref.value shouldBe "CUSTOM"
            }
        }
    }
}
