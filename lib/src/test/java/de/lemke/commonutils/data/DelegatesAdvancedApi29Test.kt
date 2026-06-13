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
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.SaveLocation
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [29])
class DelegatesAdvancedApi29Test {
    private lateinit var prefs: SharedPreferences

    @BeforeEach
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = ctx.getSharedPreferences("test_prefs_api29", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @Test
    fun `saveLocation always returns CUSTOM on API 29`() {
        class Holder {
            var location: SaveLocation by prefs.delegates.saveLocation(default = SaveLocation.DOWNLOADS)
        }
        Holder().location shouldBe SaveLocation.CUSTOM
    }
}
