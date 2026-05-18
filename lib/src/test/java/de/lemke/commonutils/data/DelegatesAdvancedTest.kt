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
@Config(sdk = [36])
class DelegatesAdvancedTest {
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = ctx.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    // region boolean

    @Test
    fun `boolean returns default when key absent`() {
        class Holder {
            var flag: Boolean by prefs.delegates.boolean(default = true)
        }
        assertThat(Holder().flag).isTrue()
    }

    @Test
    fun `boolean round-trips written value`() {
        class Holder {
            var flag: Boolean by prefs.delegates.boolean(default = false)
        }
        val h = Holder()
        h.flag = true
        assertThat(Holder().flag).isTrue()
    }

    // endregion

    // region int

    @Test
    fun `int returns default when key absent`() {
        class Holder {
            var n: Int by prefs.delegates.int(default = 42)
        }
        assertThat(Holder().n).isEqualTo(42)
    }

    @Test
    fun `int round-trips written value`() {
        class Holder {
            var n: Int by prefs.delegates.int(default = 0)
        }
        val h = Holder()
        h.n = 99
        assertThat(Holder().n).isEqualTo(99)
    }

    // endregion

    // region float

    @Test
    fun `float returns default when key absent`() {
        class Holder {
            var f: Float by prefs.delegates.float(default = 3.14f)
        }
        assertThat(Holder().f).isEqualTo(3.14f)
    }

    @Test
    fun `float round-trips written value`() {
        class Holder {
            var f: Float by prefs.delegates.float(default = 0f)
        }
        val h = Holder()
        h.f = 2.71f
        assertThat(Holder().f).isEqualTo(2.71f)
    }

    // endregion

    // region long

    @Test
    fun `long returns default when key absent`() {
        class Holder {
            var l: Long by prefs.delegates.long(default = 100L)
        }
        assertThat(Holder().l).isEqualTo(100L)
    }

    @Test
    fun `long round-trips written value`() {
        class Holder {
            var l: Long by prefs.delegates.long(default = 0L)
        }
        val h = Holder()
        h.l = Long.MAX_VALUE
        assertThat(Holder().l).isEqualTo(Long.MAX_VALUE)
    }

    // endregion

    // region string

    @Test
    fun `string returns default when key absent`() {
        class Holder {
            var s: String by prefs.delegates.string(default = "hello")
        }
        assertThat(Holder().s).isEqualTo("hello")
    }

    @Test
    fun `string round-trips written value`() {
        class Holder {
            var s: String by prefs.delegates.string(default = "")
        }
        val h = Holder()
        h.s = "world"
        assertThat(Holder().s).isEqualTo("world")
    }

    // endregion

    // region stringSet

    @Test
    fun `stringSet returns default when key absent`() {
        class Holder {
            var ss: Set<String> by prefs.delegates.stringSet(default = setOf("a", "b"))
        }
        assertThat(Holder().ss).containsExactly("a", "b")
    }

    @Test
    fun `stringSet round-trips written value`() {
        class Holder {
            var ss: Set<String> by prefs.delegates.stringSet(default = emptySet())
        }
        val h = Holder()
        h.ss = setOf("x", "y", "z")
        assertThat(Holder().ss).containsExactly("x", "y", "z")
    }

    // endregion

    // region darkMode

    @Test
    fun `darkMode returns default false when key absent`() {
        class Holder {
            var dm: Boolean by prefs.delegates.darkMode(default = false)
        }
        assertThat(Holder().dm).isFalse()
    }

    @Test
    fun `darkMode round-trips true as string 1`() {
        class Holder {
            var dm: Boolean by prefs.delegates.darkMode(default = false)
        }
        val h = Holder()
        h.dm = true
        assertThat(Holder().dm).isTrue()
        // Verify stored as "1"
        assertThat(prefs.getString("dm", null)).isEqualTo("1")
    }

    @Test
    fun `darkMode round-trips false as string 0`() {
        class Holder {
            var dm: Boolean by prefs.delegates.darkMode(default = true)
        }
        val h = Holder()
        h.dm = false
        assertThat(Holder().dm).isFalse()
        assertThat(prefs.getString("dm", null)).isEqualTo("0")
    }

    // endregion

    // region saveLocation

    @Test
    fun `saveLocation returns default when key absent`() {
        class Holder {
            var loc: SaveLocation by prefs.delegates.saveLocation(default = SaveLocation.PICTURES)
        }
        assertThat(Holder().loc).isEqualTo(SaveLocation.PICTURES)
    }

    @Test
    fun `saveLocation round-trips DOWNLOADS`() {
        class Holder {
            var loc: SaveLocation by prefs.delegates.saveLocation()
        }
        val h = Holder()
        h.loc = SaveLocation.DOWNLOADS
        assertThat(Holder().loc).isEqualTo(SaveLocation.DOWNLOADS)
    }

    @Test
    fun `saveLocation stores enum name in prefs`() {
        class Holder {
            var loc: SaveLocation by prefs.delegates.saveLocation()
        }
        val h = Holder()
        h.loc = SaveLocation.DCIM
        assertThat(prefs.getString("loc", null)).isEqualTo("DCIM")
    }

    // endregion

    // region custom key

    @Test
    fun `boolean with explicit key uses that key in prefs`() {
        class Holder {
            var flag: Boolean by prefs.delegates.boolean(default = false, key = "my_custom_key")
        }
        val h = Holder()
        h.flag = true
        assertThat(prefs.getBoolean("my_custom_key", false)).isTrue()
    }

    // endregion
}
