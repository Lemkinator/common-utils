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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import de.lemke.commonutils.SaveLocation
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRepositoryTest {
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("settings_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        repo = SettingsRepository(prefs)
    }

    @Test
    fun `darkMode defaults to false`() {
        assertThat(repo.darkMode).isFalse()
    }

    @Test
    fun `autoDarkMode defaults to true`() {
        assertThat(repo.autoDarkMode).isTrue()
    }

    @Test
    fun `lastVersionCode defaults to -1`() {
        assertThat(repo.lastVersionCode).isEqualTo(-1)
    }

    @Test
    fun `lastVersionName defaults to 0_0_0`() {
        assertThat(repo.lastVersionName).isEqualTo("0.0.0")
    }

    @Test
    fun `acceptedTosVersion defaults to -1`() {
        assertThat(repo.acceptedTosVersion).isEqualTo(-1)
    }

    @Test
    fun `devModeEnabled defaults to false`() {
        assertThat(repo.devModeEnabled).isFalse()
    }

    @Test
    fun `search defaults to empty string`() {
        assertThat(repo.search).isEmpty()
    }

    @Test
    fun `lastVersionCode round-trips written value`() {
        repo.lastVersionCode = 42
        assertThat(repo.lastVersionCode).isEqualTo(42)
    }

    @Test
    fun `darkMode round-trips written value`() {
        repo.darkMode = true
        assertThat(repo.darkMode).isTrue()
    }

    @Test
    fun `search round-trips written value`() {
        repo.search = "hello"
        assertThat(repo.search).isEqualTo("hello")
    }

    @Test
    fun `imageSaveLocation defaults to SaveLocation default`() {
        assertThat(repo.imageSaveLocation).isEqualTo(SaveLocation.default)
    }

    @Test
    fun `imageSaveLocation round-trips DOWNLOADS`() {
        repo.imageSaveLocation = SaveLocation.DOWNLOADS
        assertThat(repo.imageSaveLocation).isEqualTo(SaveLocation.DOWNLOADS)
    }

    @Test
    fun `autoDarkMode round-trips false`() {
        repo.autoDarkMode = false
        assertThat(repo.autoDarkMode).isFalse()
    }

    @Test
    fun `devModeEnabled round-trips true`() {
        repo.devModeEnabled = true
        assertThat(repo.devModeEnabled).isTrue()
    }

    @Test
    fun `acceptedTosVersion round-trips written value`() {
        repo.acceptedTosVersion = 3
        assertThat(repo.acceptedTosVersion).isEqualTo(3)
    }

    @Test
    fun `lastVersionName round-trips written value`() {
        repo.lastVersionName = "2.5.0"
        assertThat(repo.lastVersionName).isEqualTo("2.5.0")
    }
}
