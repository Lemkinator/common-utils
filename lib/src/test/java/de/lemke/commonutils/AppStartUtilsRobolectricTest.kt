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
package de.lemke.commonutils

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.data.commonUtilsSettings
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class AppStartUtilsRobolectricTest {
    private lateinit var activity: AppCompatActivity

    @BeforeEach
    fun setUp() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("appstart_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        commonUtilsSettings = SettingsRepository(prefs)
    }

    @Test
    fun `checkAppStart returns FIRST_TIME when no previous install`() {
        commonUtilsSettings.lastVersionCode = -1
        activity.checkAppStart(10, "1.0").result shouldBe AppStartResult.FIRST_TIME
    }

    @Test
    fun `checkAppStart returns FIRST_TIME_VERSION on upgrade`() {
        commonUtilsSettings.lastVersionCode = 5
        activity.checkAppStart(10, "1.0").result shouldBe AppStartResult.FIRST_TIME_VERSION
    }

    @Test
    fun `checkAppStart returns NORMAL on same version`() {
        commonUtilsSettings.lastVersionCode = 10
        activity.checkAppStart(10, "1.0").result shouldBe AppStartResult.NORMAL
    }

    @Test
    fun `checkAppStart returns NORMAL on downgrade`() {
        commonUtilsSettings.lastVersionCode = 15
        activity.checkAppStart(10, "1.0").result shouldBe AppStartResult.NORMAL
    }
}
