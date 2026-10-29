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

import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.DrainMainLooperRule
import de.lemke.commonutils.R
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.ui.utils.setupCommonUtilsSettingsActivity
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An app that does not include `CommonUtilsBackupModule` binds no `BackupScreenDeps`. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class CommonUtilsSettingsActivityWithoutBackupTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val drainMainLooper = DrainMainLooperRule()

    @BindValue
    @JvmField
    val settings: SettingsRepository = SettingsRepository(freshTestPreferences())

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        CommonUtilsSettingsActivity.preferences = emptyList()
        CommonUtilsSettingsActivity.relativeLinksCard = true
    }

    @Test
    fun `an app without the backup module shows no Backup category`() {
        setupCommonUtilsSettingsActivity(listOf(R.xml.preferences_backup), relativeLinksCard = false)

        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use {
            it.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<PreferenceFragmentCompat>()
                        .first()
                fragment.isResumed shouldBe true
                fragment.findPreference<Preference>(activity.getString(R.string.commonutils_preference_key_backup)) shouldBe null
                fragment.findPreference<Preference>(activity.getString(R.string.commonutils_preference_key_export_backup)) shouldBe null
                fragment.findPreference<Preference>(activity.getString(R.string.commonutils_preference_key_import_backup)) shouldBe null
                fragment.preferenceScreen.preferenceCount shouldBe 0
            }
        }
    }
}
