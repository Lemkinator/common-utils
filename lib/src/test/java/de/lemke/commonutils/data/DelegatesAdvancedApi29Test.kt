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
import de.lemke.commonutils.freshTestPreferences
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DelegatesAdvancedApi29Test {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = freshTestPreferences("test_prefs_api29")
    }

    @Test
    fun `saveLocation always returns CUSTOM on API 29`() {
        class Holder {
            var location: SaveLocation by prefs.delegates.saveLocation(default = SaveLocation.DOWNLOADS)
        }
        Holder().location shouldBe SaveLocation.CUSTOM
    }

    @Test
    fun `saveLocation setter on API 29 always stores CUSTOM in prefs`() {
        class Holder {
            var location: SaveLocation by prefs.delegates.saveLocation()
        }

        val h = Holder()
        h.location = SaveLocation.DOWNLOADS // setter must store CUSTOM on API <= Q
        prefs.getString("location", null) shouldBe "CUSTOM"
    }
}
