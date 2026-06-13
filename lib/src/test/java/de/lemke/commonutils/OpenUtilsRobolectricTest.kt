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
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class OpenUtilsApi36Test {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `areAppLocalSettingsSupported returns true on API 33+`() {
        areAppLocalSettingsSupported().shouldBeTrue()
    }

    @Test
    fun `openApplicationSettings fires ACTION_APPLICATION_DETAILS_SETTINGS intent`() {
        ctx.openApplicationSettings().shouldBeTrue()
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        intent shouldNotBe null
        intent.action shouldBe Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    }

    @Test
    fun `openApp with tryLocalFirst false fires Play Store intent`() {
        ctx.openApp("com.example.nonexistent", tryLocalFirst = false).shouldBeTrue()
        shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `openApp with tryLocalFirst true and unknown package falls back to store`() {
        ctx.openApp("com.example.notinstalled", tryLocalFirst = true).shouldBeTrue()
        shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity shouldNotBe null
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [32])
class OpenUtilsApi32Test {
    @Test
    fun `areAppLocalSettingsSupported returns false below API 33`() {
        areAppLocalSettingsSupported().shouldBeFalse()
    }
}
