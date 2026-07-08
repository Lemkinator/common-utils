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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class DelegatesAdvancedTest {
    private lateinit var prefs: SharedPreferences

    @BeforeEach
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = ctx.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @Test
    fun `boolean returns default when key absent`() {
        class Holder {
            var flag: Boolean by prefs.delegates.boolean(default = true)
        }
        Holder().flag.shouldBeTrue()
    }

    @Test
    fun `boolean round-trips written value`() {
        class Holder {
            var flag: Boolean by prefs.delegates.boolean(default = false)
        }

        val h = Holder()
        h.flag = true
        Holder().flag.shouldBeTrue()
    }

    @Test
    fun `int returns default when key absent`() {
        class Holder {
            var n: Int by prefs.delegates.int(default = 42)
        }
        Holder().n shouldBe 42
    }

    @Test
    fun `int round-trips written value`() {
        class Holder {
            var n: Int by prefs.delegates.int(default = 0)
        }

        val h = Holder()
        h.n = 99
        Holder().n shouldBe 99
    }

    @Test
    fun `float returns default when key absent`() {
        class Holder {
            var f: Float by prefs.delegates.float(default = 3.14f)
        }
        Holder().f shouldBe 3.14f
    }

    @Test
    fun `float round-trips written value`() {
        class Holder {
            var f: Float by prefs.delegates.float(default = 0f)
        }

        val h = Holder()
        h.f = 2.71f
        Holder().f shouldBe 2.71f
    }

    @Test
    fun `long returns default when key absent`() {
        class Holder {
            var l: Long by prefs.delegates.long(default = 100L)
        }
        Holder().l shouldBe 100L
    }

    @Test
    fun `long round-trips written value`() {
        class Holder {
            var l: Long by prefs.delegates.long(default = 0L)
        }

        val h = Holder()
        h.l = Long.MAX_VALUE
        Holder().l shouldBe Long.MAX_VALUE
    }

    @Test
    fun `string returns default when key absent`() {
        class Holder {
            var s: String by prefs.delegates.string(default = "hello")
        }
        Holder().s shouldBe "hello"
    }

    @Test
    fun `string round-trips written value`() {
        class Holder {
            var s: String by prefs.delegates.string(default = "")
        }

        val h = Holder()
        h.s = "world"
        Holder().s shouldBe "world"
    }

    @Test
    fun `stringSet returns default when key absent`() {
        class Holder {
            var ss: Set<String> by prefs.delegates.stringSet(default = setOf("a", "b"))
        }
        Holder().ss shouldContainExactlyInAnyOrder setOf("a", "b")
    }

    @Test
    fun `stringSet round-trips written value`() {
        class Holder {
            var ss: Set<String> by prefs.delegates.stringSet(default = emptySet())
        }

        val h = Holder()
        h.ss = setOf("x", "y", "z")
        Holder().ss shouldContainExactlyInAnyOrder setOf("x", "y", "z")
    }

    @Test
    fun `darkMode returns default false when key absent`() {
        class Holder {
            var dm: Boolean by prefs.delegates.darkMode(default = false)
        }
        Holder().dm.shouldBeFalse()
    }

    @Test
    fun `darkMode round-trips true as string 1`() {
        class Holder {
            var dm: Boolean by prefs.delegates.darkMode(default = false)
        }

        val h = Holder()
        h.dm = true
        Holder().dm.shouldBeTrue()
        prefs.getString("dm", null) shouldBe "1"
    }

    @Test
    fun `darkMode round-trips false as string 0`() {
        class Holder {
            var dm: Boolean by prefs.delegates.darkMode(default = true)
        }

        val h = Holder()
        h.dm = false
        Holder().dm.shouldBeFalse()
        prefs.getString("dm", null) shouldBe "0"
    }

    @Test
    fun `saveLocation returns default when key absent`() {
        class Holder {
            var loc: SaveLocation by prefs.delegates.saveLocation(default = SaveLocation.PICTURES)
        }
        Holder().loc shouldBe SaveLocation.PICTURES
    }

    @Test
    fun `saveLocation round-trips DOWNLOADS`() {
        class Holder {
            var loc: SaveLocation by prefs.delegates.saveLocation()
        }

        val h = Holder()
        h.loc = SaveLocation.DOWNLOADS
        Holder().loc shouldBe SaveLocation.DOWNLOADS
    }

    @Test
    fun `saveLocation stores enum name in prefs`() {
        class Holder {
            var loc: SaveLocation by prefs.delegates.saveLocation()
        }

        val h = Holder()
        h.loc = SaveLocation.DCIM
        prefs.getString("loc", null) shouldBe "DCIM"
    }

    @Test
    fun `intList returns default when key absent`() {
        class Holder {
            var list: List<Int> by prefs.delegates.intList(default = listOf(1, 2, 3))
        }
        Holder().list shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `intList round-trips written value`() {
        class Holder {
            var list: List<Int> by prefs.delegates.intList(default = emptyList())
        }

        val h = Holder()
        h.list = listOf(4, 5, 6)
        Holder().list shouldBe listOf(4, 5, 6)
        prefs.getString("list", null) shouldBe "4,5,6"
    }

    @Test
    fun `intList returns default when stored value has no parsable ints`() {
        prefs.edit().putString("list", "a,b,c").apply()

        class Holder {
            var list: List<Int> by prefs.delegates.intList(default = listOf(9))
        }
        Holder().list shouldBe listOf(9)
    }

    @Test
    fun `intList filters out empty segments produced by consecutive commas`() {
        prefs.edit().putString("list", "1,,2").apply()

        class Holder {
            var list: List<Int> by prefs.delegates.intList(default = emptyList())
        }
        Holder().list shouldBe listOf(1, 2)
    }

    @Test
    fun `intList parses negative ints`() {
        prefs.edit().putString("list", "-1,2,-3").apply()

        class Holder {
            var list: List<Int> by prefs.delegates.intList(default = emptyList())
        }
        Holder().list shouldBe listOf(-1, 2, -3)
    }

    @Test
    fun `intList with all-defaults produces a working delegate`() {
        class Holder {
            var list: List<Int> by prefs.delegates.intList()
        }

        val h = Holder()
        h.list shouldBe emptyList()
        h.list = listOf(1)
        Holder().list shouldBe listOf(1)
    }

    @Test
    fun `intList with explicit key uses that key in prefs`() {
        class Holder {
            var list: List<Int> by prefs.delegates.intList(default = emptyList(), key = "my_int_list_key")
        }

        val h = Holder()
        h.list = listOf(7, 8)
        prefs.getString("my_int_list_key", null) shouldBe "7,8"
    }

    @Test
    fun `sanitized clamps a value already out of range in storage on read`() {
        prefs.edit().putInt("size", 9999).apply()

        class Holder {
            var size: Int by prefs.delegates.int(default = 512).sanitized { it.coerceIn(16, 1024) }
        }
        Holder().size shouldBe 1024
    }

    @Test
    fun `sanitized clamps on write so the stored value itself is valid`() {
        class Holder {
            var size: Int by prefs.delegates.int(default = 512).sanitized { it.coerceIn(16, 1024) }
        }

        val h = Holder()
        h.size = -5
        prefs.getInt("size", -1) shouldBe 16
    }

    @Test
    fun `sanitized passes through in-range values unchanged`() {
        class Holder {
            var size: Int by prefs.delegates.int(default = 512).sanitized { it.coerceIn(16, 1024) }
        }

        val h = Holder()
        h.size = 256
        Holder().size shouldBe 256
    }

    @Test
    fun `sanitized composes with intList to cap a stored list on read`() {
        prefs.edit().putString("colors", "1,2,3,4,5,6,7,8").apply()

        class Holder {
            var colors: List<Int> by prefs.delegates.intList(default = emptyList()).sanitized { it.take(6) }
        }
        Holder().colors shouldBe listOf(1, 2, 3, 4, 5, 6)
    }

    @Test
    fun `boolean with explicit key uses that key in prefs`() {
        class Holder {
            var flag: Boolean by prefs.delegates.boolean(default = false, key = "my_custom_key")
        }

        val h = Holder()
        h.flag = true
        prefs.getBoolean("my_custom_key", false) shouldBe true
    }

    @Test
    fun `string returns default when stored value is null`() {
        // Store null explicitly → getString returns null → ?: default kicks in
        prefs.edit().putString("s", null).apply()

        class Holder {
            var s: String by prefs.delegates.string(default = "fallback")
        }
        Holder().s shouldBe "fallback"
    }

    @Test
    fun `stringSet returns default when stored value is null`() {
        prefs.edit().putStringSet("ss", null).apply()

        class Holder {
            var ss: Set<String> by prefs.delegates.stringSet(default = setOf("default"))
        }
        Holder().ss shouldContainExactlyInAnyOrder setOf("default")
    }

    @Test
    fun `string with explicit key uses that key in prefs`() {
        class Holder {
            var s: String by prefs.delegates.string(default = "", key = "my_string_key")
        }

        val h = Holder()
        h.s = "hello"
        prefs.getString("my_string_key", null) shouldBe "hello"
    }

    @Test
    fun `stringSet with explicit key uses that key in prefs`() {
        class Holder {
            var ss: Set<String> by prefs.delegates.stringSet(default = emptySet(), key = "my_set_key")
        }

        val h = Holder()
        h.ss = setOf("x", "y")
        prefs.getStringSet("my_set_key", null) shouldContainExactlyInAnyOrder setOf("x", "y")
    }

    @Test
    fun `boolean with all-defaults produces a working delegate`() {
        class Holder {
            var flag: Boolean by prefs.delegates.boolean()
        }

        val h = Holder()
        h.flag.shouldBeFalse()
        h.flag = true
        Holder().flag.shouldBeTrue()
    }

    @Test
    fun `int with all-defaults produces a working delegate`() {
        class Holder {
            var n: Int by prefs.delegates.int()
        }

        val h = Holder()
        h.n shouldBe 0
        h.n = 7
        Holder().n shouldBe 7
    }

    @Test
    fun `float with all-defaults produces a working delegate`() {
        class Holder {
            var f: Float by prefs.delegates.float()
        }

        val h = Holder()
        h.f shouldBe 0f
        h.f = 1.5f
        Holder().f shouldBe 1.5f
    }

    @Test
    fun `long with all-defaults produces a working delegate`() {
        class Holder {
            var l: Long by prefs.delegates.long()
        }

        val h = Holder()
        h.l shouldBe 0L
        h.l = 42L
        Holder().l shouldBe 42L
    }

    @Test
    fun `string with all-defaults produces a working delegate`() {
        class Holder {
            var s: String by prefs.delegates.string()
        }

        val h = Holder()
        h.s shouldBe ""
        h.s = "hello"
        Holder().s shouldBe "hello"
    }

    @Test
    fun `stringSet with all-defaults produces a working delegate`() {
        class Holder {
            var ss: Set<String> by prefs.delegates.stringSet()
        }

        val h = Holder()
        h.ss shouldContainExactlyInAnyOrder emptySet()
        h.ss = setOf("a")
        Holder().ss shouldContainExactlyInAnyOrder setOf("a")
    }

    @Test
    fun `darkMode with all-defaults produces a working delegate`() {
        class Holder {
            var dm: Boolean by prefs.delegates.darkMode()
        }

        val h = Holder()
        h.dm.shouldBeFalse()
        h.dm = true
        Holder().dm.shouldBeTrue()
    }

    @Test
    fun `saveLocation with all-defaults produces a working delegate`() {
        class Holder {
            var loc: SaveLocation by prefs.delegates.saveLocation()
        }

        val h = Holder()
        h.loc shouldBe SaveLocation.CUSTOM
        h.loc = SaveLocation.PICTURES
        Holder().loc shouldBe SaveLocation.PICTURES
    }
}
