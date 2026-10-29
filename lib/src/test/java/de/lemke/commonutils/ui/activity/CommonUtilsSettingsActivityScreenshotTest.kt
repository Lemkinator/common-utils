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

import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.R
import de.lemke.commonutils.backup.BackupScreenDeps
import de.lemke.commonutils.backup.FakeBackupDocuments
import de.lemke.commonutils.backup.core.BackupApp
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.commonutils.backup.core.MemorySection
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.ui.utils.setupCommonUtilsSettingsActivity
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JUnit4 (not JUnit5/Kotest like the rest of this module): `HiltAndroidRule`/`@HiltAndroidTest`
 * require a JUnit4 `@RunWith(RobolectricTestRunner::class)` runner, so this class runs under the
 * `junit-vintage-engine` island — see `lib/build.gradle.kts` test dependencies.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CommonUtilsSettingsActivityScreenshotTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val fakeSettings: SettingsRepository = SettingsRepository(freshTestPreferences())

    @BindValue
    @JvmField
    val backupScreen: BackupScreenDeps =
        BackupScreenDeps(
            BackupEngine(
                setOf(MemorySection()),
                object : BackupTransaction {
                    override suspend fun <R> run(block: suspend () -> R): R = block()
                },
                BackupApp("screenshot", 1),
                { "2026-10-27T14:30:00+01:00" },
            ),
            FakeBackupDocuments(),
            emptySet(),
            Dispatchers.Unconfined,
        )

    @Before
    fun setUp() {
        // AppCompatDelegate.setDefaultNightMode() is a real static singleton, not a Robolectric
        // shadow - reset before each test so an earlier test's leftover mode can't override how
        // the "+night" qualifier renders here (a non-FOLLOW_SYSTEM mode pins day/night regardless
        // of the resource qualifier).
        setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        hiltRule.inject()
        setupCommonUtilsSettingsActivity(
            R.xml.preferences_design,
            R.xml.preferences_general_language_and_image_save_location,
            R.xml.preferences_dev_options_delete_app_data,
            R.xml.preferences_more_info,
            relativeLinksCard = false,
        )
    }

    @After
    fun tearDown() {
        setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        CommonUtilsSettingsActivity.preferences =
            listOf(
                R.xml.preferences_design,
                R.xml.preferences_general_language,
                R.xml.preferences_dev_options_delete_app_data,
                R.xml.preferences_more_info,
            )
        CommonUtilsSettingsActivity.relativeLinksCard = true
    }

    @Test
    fun settingsActivity_default() {
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use {
            onView(isRoot()).captureRoboImage("src/test/screenshots/settings_default.png")
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun settingsActivity_default_dark() {
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use {
            onView(isRoot()).captureRoboImage("src/test/screenshots/settings_default_dark.png")
        }
    }

    @Test
    fun settingsActivity_backup() {
        CommonUtilsSettingsActivity.preferences = listOf(R.xml.preferences_design, R.xml.preferences_backup)
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use {
            onView(isRoot()).captureRoboImage("src/test/screenshots/settings_backup.png")
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun settingsActivity_backup_dark() {
        CommonUtilsSettingsActivity.preferences = listOf(R.xml.preferences_design, R.xml.preferences_backup)
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use {
            onView(isRoot()).captureRoboImage("src/test/screenshots/settings_backup_dark.png")
        }
    }
}
