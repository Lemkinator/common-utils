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
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.freshTestPreferences
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var target: SharedPreferences

    @Before
    fun setUp() {
        target = freshTestPreferences(context)
    }

    @Test
    fun `copies every value type from a DataStore file and deletes the file`() {
        val file =
            writeDataStore(
                "userSettings",
                booleanPreferencesKey("maskEnabled") to true,
                intPreferencesKey("iconSize") to 128,
                longPreferencesKey("installTime") to 1_700_000_000_000L,
                floatPreferencesKey("scale") to 1.5f,
                stringPreferencesKey("buchId") to "gb",
                stringSetPreferencesKey("favorites") to setOf("12", "34"),
            )

        target.migrateSettings(context) {
            dataStore("userSettings") { keys("maskEnabled", "iconSize", "installTime", "scale", "buchId", "favorites") }
        }

        target.getBoolean("maskEnabled", false).shouldBeTrue()
        target.getInt("iconSize", 0) shouldBe 128
        target.getLong("installTime", 0L) shouldBe 1_700_000_000_000L
        target.getFloat("scale", 0f) shouldBe 1.5f
        target.getString("buchId", null) shouldBe "gb"
        target.getStringSet("favorites", null) shouldBe setOf("12", "34")
        file.exists().shouldBeFalse()
    }

    @Test
    fun `renames a DataStore key`() {
        writeDataStore("userSettings", intPreferencesKey("textsize") to 24)

        target.migrateSettings(context) { dataStore("userSettings") { key("textsize", to = "textSize") } }

        target.all shouldBe mapOf("textSize" to 24)
    }

    @Test
    fun `converts a value before storing it`() {
        writeDataStore("userSettings", intPreferencesKey("errorLimit") to 5)

        target.migrateSettings(context) { dataStore("userSettings") { key("errorLimit") { it.toString() } } }

        target.all shouldBe mapOf("errorLimit" to "5")
    }

    @Test
    fun `drops a value that the conversion rejects`() {
        val file = writeDataStore("userSettings", stringPreferencesKey("theme") to "sepia")

        target.migrateSettings(context) { dataStore("userSettings") { key("theme") { null } } }

        target.all.shouldBeEmpty()
        file.exists().shouldBeFalse()
    }

    @Test
    fun `drops a value whose conversion throws and still migrates the rest`() {
        val file =
            writeDataStore(
                "userSettings",
                stringPreferencesKey("errorLimit") to "five",
                intPreferencesKey("iconSize") to 128,
            )

        target.migrateSettings(context) {
            dataStore("userSettings") {
                key("errorLimit") { (it as String).toInt() }
                keys("iconSize")
            }
        }

        target.all shouldBe mapOf("iconSize" to 128)
        file.exists().shouldBeFalse()
        val log = ShadowLog.getLogsForTag("SettingsMigration").single()
        log.type shouldBe Log.WARN
        log.msg shouldBe "Dropping errorLimit: its conversion failed"
        log.throwable.shouldBeInstanceOf<NumberFormatException>()
    }

    @Test
    fun `an Error thrown by a conversion propagates and keeps the source`() {
        val file =
            writeDataStore(
                "userSettings",
                intPreferencesKey("iconSize") to 128,
                booleanPreferencesKey("maskEnabled") to true,
            )

        shouldThrow<OutOfMemoryError> {
            target.migrateSettings(context) {
                dataStore("userSettings") {
                    keys("iconSize")
                    key("maskEnabled") { throw OutOfMemoryError("convert") }
                }
            }
        }.message shouldBe "convert"

        target.all.shouldBeEmpty()
        file.exists().shouldBeTrue()
        ShadowLog.getLogsForTag("SettingsMigration") shouldBe emptyList()
    }

    @Test
    fun `drops a value SharedPreferences cannot store`() {
        val file = writeDataStore("userSettings", doublePreferencesKey("ratio") to 0.75)

        target.migrateSettings(context) { dataStore("userSettings") { keys("ratio") } }

        target.all.shouldBeEmpty()
        file.exists().shouldBeFalse()
    }

    @Test
    fun `drops a set whose elements are not all Strings`() {
        val file = writeDataStore("userSettings", stringSetPreferencesKey("favorites") to setOf("12", "34"))

        target.migrateSettings(context) { dataStore("userSettings") { key("favorites") { setOf(12, 34) } } }

        target.all.shouldBeEmpty()
        file.exists().shouldBeFalse()
        val log = ShadowLog.getLogsForTag("SettingsMigration").single()
        log.type shouldBe Log.WARN
        log.msg shouldBe "Dropping favorites: SharedPreferences cannot store LinkedHashSet"
    }

    @Test
    fun `never overwrites a key that already exists in the target`() {
        target.edit(commit = true) { putInt("iconSize", 64) }
        writeDataStore("userSettings", intPreferencesKey("iconSize") to 128)

        target.migrateSettings(context) { dataStore("userSettings") { keys("iconSize") } }

        target.all shouldBe mapOf("iconSize" to 64)
    }

    @Test
    fun `a DataStore declared first wins over a later rename`() {
        target.edit(commit = true) { putBoolean("auto_copy_on_create_pref", false) }
        writeDataStore("userSettings", booleanPreferencesKey("autoCopyOnCreate") to true)

        target.migrateSettings(context) {
            dataStore("userSettings") { keys("autoCopyOnCreate") }
            renames { key("auto_copy_on_create_pref", to = "autoCopyOnCreate") }
        }

        target.all shouldBe mapOf("autoCopyOnCreate" to true)
    }

    @Test
    fun `a rename declared first wins over a later DataStore`() {
        target.edit(commit = true) { putBoolean("auto_copy_on_create_pref", false) }
        val file = writeDataStore("userSettings", booleanPreferencesKey("autoCopyOnCreate") to true)

        target.migrateSettings(context) {
            renames { key("auto_copy_on_create_pref", to = "autoCopyOnCreate") }
            dataStore("userSettings") { keys("autoCopyOnCreate") }
        }

        target.all shouldBe mapOf("autoCopyOnCreate" to false)
        file.exists().shouldBeFalse()
    }

    @Test
    fun `a later source fills a key the first source lacks`() {
        writeDataStore("userSettings", intPreferencesKey("iconSize") to 128)
        target.edit(commit = true) { putBoolean("auto_copy_on_create_pref", true) }

        target.migrateSettings(context) {
            dataStore("userSettings") { keys("iconSize", "autoCopyOnCreate") }
            renames { key("auto_copy_on_create_pref", to = "autoCopyOnCreate") }
        }

        target.all shouldBe mapOf("iconSize" to 128, "autoCopyOnCreate" to true)
    }

    @Test
    fun `a rename and a SharedPreferences source drop their old key when the target already holds the new key`() {
        target.edit(commit = true) {
            putBoolean("pdfPageSnap", false)
            putBoolean("pdf_page_snap_pref", true)
            putLong("lastSync", 1_600_000_000_000L)
        }
        context.getSharedPreferences("legacy", MODE_PRIVATE).edit(commit = true) {
            putLong("lastSync", 1_700_000_000_000L)
            putInt("unrelated", 2)
        }

        target.migrateSettings(context) {
            sharedPreferences("legacy") { keys("lastSync") }
            renames { key("pdf_page_snap_pref", to = "pdfPageSnap") }
        }

        target.all shouldBe mapOf("pdfPageSnap" to false, "lastSync" to 1_600_000_000_000L)
        context.getSharedPreferences("legacy", MODE_PRIVATE).all shouldBe mapOf("unrelated" to 2)
    }

    @Test
    fun `a second run changes nothing`() {
        val file = writeDataStore("userSettings", intPreferencesKey("iconSize") to 128)
        val migrations: SettingsMigration.() -> Unit = { dataStore("userSettings") { keys("iconSize") } }
        target.migrateSettings(context, migrations)
        target.edit(commit = true) { putInt("iconSize", 256) }

        target.migrateSettings(context, migrations)

        target.all shouldBe mapOf("iconSize" to 256)
        file.exists().shouldBeFalse()
    }

    @Test
    fun `a listed key the source never stored stays absent`() {
        writeDataStore("userSettings", intPreferencesKey("iconSize") to 128)

        target.migrateSettings(context) { dataStore("userSettings") { keys("iconSize", "maskEnabled") } }

        target.all shouldBe mapOf("iconSize" to 128)
    }

    @Test
    fun `a missing DataStore file is a no-op`() {
        val result = target.migrateSettings(context) { dataStore("userSettings") { keys("iconSize") } }

        result shouldBeSameInstanceAs target
        target.all.shouldBeEmpty()
    }

    @Test
    fun `a corrupt DataStore file is skipped and kept while other sources still migrate`() {
        val dir = File(context.filesDir, "datastore").apply { mkdirs() }
        val file = File(dir, "userSettings.preferences_pb").apply { writeBytes(byteArrayOf(0x0F)) }
        target.edit(commit = true) { putInt("hymnNumber", 7) }

        target.migrateSettings(context) {
            dataStore("userSettings") { keys("iconSize") }
            renames { key("hymnNumber", to = "number") }
        }

        file.readBytes() shouldBe byteArrayOf(0x0F)
        target.all shouldBe mapOf("number" to 7)
    }

    @Test
    fun `moves listed keys out of a SharedPreferences file and deletes the emptied file`() {
        context.getSharedPreferences("legacy", MODE_PRIVATE).edit(commit = true) {
            putLong("lastSync", 1_700_000_000_000L)
            putStringSet("tags", setOf("a", "b"))
        }

        target.migrateSettings(context) { sharedPreferences("legacy") { keys("lastSync", "tags") } }

        target.all shouldBe mapOf("lastSync" to 1_700_000_000_000L, "tags" to setOf("a", "b"))
        File(context.dataDir, "shared_prefs/legacy.xml").exists().shouldBeFalse()
    }

    @Test
    fun `keeps unlisted keys in a SharedPreferences file`() {
        context.getSharedPreferences("legacy", MODE_PRIVATE).edit(commit = true) {
            putInt("moved", 1)
            putInt("unrelated", 2)
        }

        target.migrateSettings(context) { sharedPreferences("legacy") { keys("moved") } }

        target.all shouldBe mapOf("moved" to 1)
        context.getSharedPreferences("legacy", MODE_PRIVATE).all shouldBe mapOf("unrelated" to 2)
    }

    @Test
    fun `renames a key inside the target`() {
        target.edit(commit = true) { putBoolean("pdf_page_snap_pref", true) }

        target.migrateSettings(context) { renames { key("pdf_page_snap_pref", to = "pdfPageSnap") } }

        target.all shouldBe mapOf("pdfPageSnap" to true)
    }

    @Test
    fun `a rename to the same key is rejected`() {
        shouldThrow<IllegalArgumentException> {
            target.migrateSettings(context) { renames { key("textSize", to = "textSize") } }
        }.message shouldBe "A rename needs a new key name"
    }

    @Test
    fun `commits the target to disk before returning`() {
        val named = context.getSharedPreferences("target", MODE_PRIVATE)
        writeDataStore("userSettings", intPreferencesKey("iconSize") to 128)

        named.migrateSettings(context) { dataStore("userSettings") { keys("iconSize") } }

        File(context.dataDir, "shared_prefs/target.xml").readText() shouldContain """<int name="iconSize" value="128" />"""
    }

    @Test
    fun `keeps every source when the target commit fails`() {
        val file = writeDataStore("userSettings", intPreferencesKey("iconSize") to 128)

        FailingCommitPreferences(target).migrateSettings(context) { dataStore("userSettings") { keys("iconSize") } }

        target.all.shouldBeEmpty()
        file.exists().shouldBeTrue()
        val log = ShadowLog.getLogsForTag("SettingsMigration").single()
        log.type shouldBe Log.WARN
        log.msg shouldBe "Keeping every source: the target commit failed"
    }

    @Test
    fun `keeps a rename's old key and a SharedPreferences source when the target commit fails`() {
        target.edit(commit = true) { putBoolean("pdf_page_snap_pref", true) }
        context.getSharedPreferences("legacy", MODE_PRIVATE).edit(commit = true) { putLong("lastSync", 1_700_000_000_000L) }

        FailingCommitPreferences(target).migrateSettings(context) {
            sharedPreferences("legacy") { keys("lastSync") }
            renames { key("pdf_page_snap_pref", to = "pdfPageSnap") }
        }

        target.all shouldBe mapOf("pdf_page_snap_pref" to true)
        context.getSharedPreferences("legacy", MODE_PRIVATE).all shouldBe mapOf("lastSync" to 1_700_000_000_000L)
        File(context.dataDir, "shared_prefs/legacy.xml").exists().shouldBeTrue()
    }

    @Test
    fun `moves lastInAppReview out of the legacy InAppReviewUtils file`() {
        context.getSharedPreferences("InAppReviewUtils", MODE_PRIVATE).edit(commit = true) {
            putLong("lastInAppReview", 1_700_000_000_000L)
        }

        val settings = SettingsRepository(target.migrateSettings(context))

        settings.lastInAppReview shouldBe 1_700_000_000_000L
        settings.canShowInAppReview().shouldBeTrue()
        File(context.dataDir, "shared_prefs/InAppReviewUtils.xml").exists().shouldBeFalse()
    }

    private fun writeDataStore(
        name: String,
        vararg values: Preferences.Pair<*>,
    ): File {
        val dir = File(context.filesDir, "datastore").apply { mkdirs() }
        return File(dir, "$name.preferences_pb").apply {
            outputStream().use { runBlocking { PreferencesFileSerializer.writeTo(preferencesOf(*values), it) } }
        }
    }
}

private class FailingCommitPreferences(
    private val real: SharedPreferences,
) : SharedPreferences by real {
    override fun edit(): SharedPreferences.Editor =
        object : SharedPreferences.Editor by real.edit() {
            override fun commit() = false
        }
}
