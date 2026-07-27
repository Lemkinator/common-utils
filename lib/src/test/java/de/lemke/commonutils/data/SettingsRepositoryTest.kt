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
package de.lemke.commonutils.data

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import app.cash.turbine.test
import de.lemke.commonutils.freshTestPreferences
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsRepositoryTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var repo: SettingsRepository

    private fun reload() = SettingsRepository(prefs)

    @Before
    fun setUp() {
        prefs = freshTestPreferences()
        repo = SettingsRepository(prefs)
    }

    @Test
    fun `darkMode defaults to false`() {
        repo.darkMode.shouldBeFalse()
    }

    @Test
    fun `autoDarkMode defaults to true`() {
        repo.autoDarkMode.shouldBeTrue()
    }

    @Test
    fun `lastVersionCode defaults to -1`() {
        repo.lastVersionCode shouldBe -1
    }

    @Test
    fun `lastVersionName defaults to 0_0_0`() {
        repo.lastVersionName shouldBe "0.0.0"
    }

    @Test
    fun `acceptedTosVersion defaults to -1`() {
        repo.acceptedTosVersion shouldBe -1
    }

    @Test
    fun `devModeEnabled defaults to false`() {
        repo.devModeEnabled.shouldBeFalse()
    }

    @Test
    fun `search defaults to empty string`() {
        repo.search.shouldBeEmpty()
    }

    @Test
    fun `imageSaveLocation defaults to SaveLocation default`() {
        repo.imageSaveLocation shouldBe SaveLocation.default
    }

    @Test
    fun `lastVersionCode round-trips written value`() {
        repo.lastVersionCode = 42
        reload().lastVersionCode shouldBe 42
    }

    @Test
    fun `darkMode round-trips written value`() {
        repo.darkMode = true
        reload().darkMode.shouldBeTrue()
    }

    @Test
    fun `search round-trips written value`() {
        repo.search = "hello"
        reload().search shouldBe "hello"
    }

    @Test
    fun `imageSaveLocation round-trips DOWNLOADS`() {
        repo.imageSaveLocation = SaveLocation.DOWNLOADS
        reload().imageSaveLocation shouldBe SaveLocation.DOWNLOADS
    }

    @Test
    fun `autoDarkMode round-trips false`() {
        repo.autoDarkMode = false
        reload().autoDarkMode.shouldBeFalse()
    }

    @Test
    fun `devModeEnabled round-trips true`() {
        repo.devModeEnabled = true
        reload().devModeEnabled.shouldBeTrue()
    }

    @Test
    fun `acceptedTosVersion round-trips written value`() {
        repo.acceptedTosVersion = 3
        reload().acceptedTosVersion shouldBe 3
    }

    @Test
    fun `lastVersionName round-trips written value`() {
        repo.lastVersionName = "2.5.0"
        reload().lastVersionName shouldBe "2.5.0"
    }

    // The tests below pin each property to its literal SharedPreferences key string, read back via the raw
    // SharedPreferences accessor rather than through the (possibly-renamed) property. Delegates default their key to
    // `property.name` via reflection, so an IDE rename of the property silently changes the stored key without a
    // compile error - these tests catch that by hardcoding the key string instead of round-tripping through the property.

    @Test
    fun `darkMode persists under its literal SharedPreferences key`() {
        repo.darkMode = true
        prefs.getString("darkMode", "0") shouldBe "1"
    }

    @Test
    fun `autoDarkMode persists under its literal SharedPreferences key`() {
        repo.autoDarkMode = false
        prefs.getBoolean("autoDarkMode", true) shouldBe false
    }

    @Test
    fun `lastVersionCode persists under its literal SharedPreferences key`() {
        repo.lastVersionCode = 42
        prefs.getInt("lastVersionCode", -1) shouldBe 42
    }

    @Test
    fun `lastVersionName persists under its literal SharedPreferences key`() {
        repo.lastVersionName = "2.5.0"
        prefs.getString("lastVersionName", "0.0.0") shouldBe "2.5.0"
    }

    @Test
    fun `acceptedTosVersion persists under its literal SharedPreferences key`() {
        repo.acceptedTosVersion = 3
        prefs.getInt("acceptedTosVersion", -1) shouldBe 3
    }

    @Test
    fun `devModeEnabled persists under its literal SharedPreferences key`() {
        repo.devModeEnabled = true
        prefs.getBoolean("devModeEnabled", false) shouldBe true
    }

    @Test
    fun `search persists under its literal SharedPreferences key`() {
        repo.search = "hello"
        prefs.getString("search", "") shouldBe "hello"
    }

    @Test
    fun `imageSaveLocation persists under its literal SharedPreferences key`() {
        repo.imageSaveLocation = SaveLocation.DOWNLOADS
        prefs.getString("imageSaveLocation", SaveLocation.default.name) shouldBe SaveLocation.DOWNLOADS.name
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApplyDarkModeTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = freshTestPreferences()
    }

    @Test
    fun `applyDarkMode sets FOLLOW_SYSTEM when autoDarkMode is true`() {
        val repo = SettingsRepository(prefs).apply { autoDarkMode = true }
        repo.applyDarkMode()
        AppCompatDelegate.getDefaultNightMode() shouldBe AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    @Test
    fun `applyDarkMode sets MODE_NIGHT_YES when autoDarkMode false and darkMode true`() {
        val repo =
            SettingsRepository(prefs).apply {
                autoDarkMode = false
                darkMode = true
            }
        repo.applyDarkMode()
        AppCompatDelegate.getDefaultNightMode() shouldBe AppCompatDelegate.MODE_NIGHT_YES
    }

    @Test
    fun `applyDarkMode sets MODE_NIGHT_NO when autoDarkMode and darkMode are both false`() {
        val repo =
            SettingsRepository(prefs).apply {
                autoDarkMode = false
                darkMode = false
            }
        repo.applyDarkMode()
        AppCompatDelegate.getDefaultNightMode() shouldBe AppCompatDelegate.MODE_NIGHT_NO
    }
}

private class FlowSettings(
    preferences: SharedPreferences,
) : SettingsRepository(preferences) {
    fun devModeFlow(scope: CoroutineScope): StateFlow<Boolean> = settingsFlow(scope) { devModeEnabled }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsFlowTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = freshTestPreferences()
    }

    @Test
    fun `settingsFlow emits initial snapshot and updates on preference change`() =
        runTest {
            val repo = FlowSettings(prefs)
            repo.devModeFlow(backgroundScope).test {
                awaitItem() shouldBe false
                repo.devModeEnabled = true
                awaitItem() shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `settingsFlow does not emit a duplicate item when the same value is written twice`() =
        runTest {
            val repo = FlowSettings(prefs)
            repo.devModeFlow(backgroundScope).test {
                awaitItem() shouldBe false
                repo.devModeEnabled = false
                expectNoEvents()
                repo.devModeEnabled = true
                awaitItem() shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }
}
