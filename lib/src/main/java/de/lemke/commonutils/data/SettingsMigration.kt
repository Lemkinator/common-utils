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
import androidx.datastore.preferences.core.PreferencesFileSerializer
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking

private const val TAG = "SettingsMigration"

/**
 * Moves settings that older app versions stored elsewhere into this store and returns it. Call it once at app start, before
 * anything reads settings, e.g. in the Hilt provider of the app's [SettingsRepository]:
 * ```
 * UserSettings(
 *     PreferenceManager.getDefaultSharedPreferences(context).migrateSettings(context) {
 *         dataStore("userSettings") {
 *             keys("iconSize", "maskEnabled")
 *             key("textsize", to = "textSize")
 *             key("errorLimit") { it.toString() }
 *         }
 *         renames { key("pdf_page_snap_pref", to = "pdfPageSnap") }
 *     },
 * )
 * ```
 * Every call first moves [SettingsRepository.lastInAppReview] out of the library's former `InAppReviewUtils` file, ahead of
 * the sources declared in [migrations].
 *
 * - A key that already exists in this store is never overwritten. Among the sources, the first declared one wins.
 * - The writes are committed before this returns. Only then is each source removed: a DataStore file is deleted, a
 *   SharedPreferences file loses the listed keys (and is deleted once empty), and a rename drops its old key in the same
 *   commit. A second call therefore finds nothing left to move.
 * - A missing source is skipped. A DataStore file that fails to read is logged, skipped and kept on disk.
 * - A value whose `convert` throws is logged and dropped, like a value SharedPreferences cannot store. Its source is still
 *   removed, so startup never fails on it.
 */
fun SharedPreferences.migrateSettings(
    context: Context,
    migrations: SettingsMigration.() -> Unit = {},
): SharedPreferences {
    val sources =
        SettingsMigration()
            .apply {
                sharedPreferences("InAppReviewUtils") { keys(SettingsRepository::lastInAppReview.name) }
                migrations()
            }.sources
    val found = sources.mapNotNull { source -> source.read(context, this)?.let { source to it } }
    if (found.isEmpty()) return this
    val editor = edit()
    val written = mutableSetOf<String>()
    found.forEach { (source, values) ->
        source.mappings.forEach { key -> migrateValue(key, values[key.from], editor, written) }
        source.stage(editor)
    }
    if (editor.commit()) found.forEach { (source, _) -> source.cleanUp(context) }
    return this
}

/** Scopes the [migrateSettings] builders, so a key list cannot declare another source. */
@DslMarker
annotation class SettingsMigrationDsl

/** Declares the sources [migrateSettings] reads, in priority order. */
@SettingsMigrationDsl
class SettingsMigration internal constructor() {
    internal val sources = mutableListOf<SettingsSource>()

    /** Takes [keys] from the DataStore preferences file `files/datastore/<name>.preferences_pb`, then deletes that file. */
    fun dataStore(
        name: String,
        keys: KeyMigrations.() -> Unit,
    ) {
        sources += DataStoreSource(name, KeyMigrations().apply(keys).list)
    }

    /** Takes [keys] from the SharedPreferences file [name], then removes them there, and the file once it is empty. */
    fun sharedPreferences(
        name: String,
        keys: KeyMigrations.() -> Unit,
    ) {
        sources += SharedPreferencesSource(name, KeyMigrations().apply(keys).list)
    }

    /** Moves [keys] to new names inside the target store itself; each key needs a `to` that differs from its old name. */
    fun renames(keys: KeyMigrations.() -> Unit) {
        val renames = KeyMigrations().apply(keys).list
        require(renames.none { it.from == it.to }) { "A rename needs a new key name" }
        sources += RenameSource(renames)
    }
}

/** Lists the keys one source hands over to [migrateSettings]. */
@SettingsMigrationDsl
class KeyMigrations internal constructor() {
    internal val list = mutableListOf<KeyMigration>()

    /**
     * Moves the value stored under [from] to [to]. [convert] receives the stored value and returns what to store: a Boolean,
     * Int, Long, Float, String or Set<String>. It returns null to drop the value; any other type is dropped as well. If it
     * throws, the value is logged and dropped.
     */
    fun key(
        from: String,
        to: String = from,
        convert: (Any) -> Any? = { it },
    ) {
        list += KeyMigration(from, to, convert)
    }

    /** Moves each of [names] unchanged under the same name. */
    fun keys(vararg names: String) = names.forEach { key(it) }
}

internal class KeyMigration(
    val from: String,
    val to: String,
    val convert: (Any) -> Any?,
)

internal abstract class SettingsSource(
    val mappings: List<KeyMigration>,
) {
    /** The values of this source, or null when it holds nothing to move. */
    abstract fun read(
        context: Context,
        target: SharedPreferences,
    ): Map<String, *>?

    /** Adds this source's own removals to the target's pending commit. */
    open fun stage(editor: SharedPreferences.Editor) = Unit

    /** Removes this source once the target commit has succeeded. */
    open fun cleanUp(context: Context) = Unit

    protected fun Map<String, *>.ifAnyListed() = takeIf { values -> mappings.any { it.from in values } }
}

private class DataStoreSource(
    private val name: String,
    mappings: List<KeyMigration>,
) : SettingsSource(mappings) {
    override fun read(
        context: Context,
        target: SharedPreferences,
    ): Map<String, Any>? {
        val file = file(context)
        if (!file.exists()) return null
        return try {
            file
                .inputStream()
                .use { runBlocking { PreferencesFileSerializer.readFrom(it) } }
                .asMap()
                .mapKeys { it.key.name }
        } catch (e: IOException) {
            Log.w(TAG, "Keeping unreadable DataStore file ${file.name}", e)
            null
        }
    }

    override fun cleanUp(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(context.filesDir, "datastore/$name.preferences_pb")
}

private class SharedPreferencesSource(
    private val name: String,
    mappings: List<KeyMigration>,
) : SettingsSource(mappings) {
    override fun read(
        context: Context,
        target: SharedPreferences,
    ) = context.getSharedPreferences(name, MODE_PRIVATE).all.ifAnyListed()

    override fun cleanUp(context: Context) {
        val prefs = context.getSharedPreferences(name, MODE_PRIVATE)
        val listed = mappings.map { it.from }.toSet()
        if (listed.containsAll(prefs.all.keys)) {
            context.deleteSharedPreferences(name)
        } else {
            prefs.edit { listed.forEach { remove(it) } }
        }
    }
}

private class RenameSource(
    mappings: List<KeyMigration>,
) : SettingsSource(mappings) {
    override fun read(
        context: Context,
        target: SharedPreferences,
    ) = target.all.ifAnyListed()

    override fun stage(editor: SharedPreferences.Editor) {
        mappings.forEach { editor.remove(it.from) }
    }
}

private fun SharedPreferences.migrateValue(
    key: KeyMigration,
    value: Any?,
    editor: SharedPreferences.Editor,
    written: MutableSet<String>,
) {
    if (value == null || key.to in written || contains(key.to)) return
    val converted =
        runCatching { key.convert(value) }
            .onFailure { Log.w(TAG, "Dropping ${key.to}: its conversion failed", it) }
            .getOrNull() ?: return
    if (editor.putSetting(key.to, converted)) written += key.to
}

private fun SharedPreferences.Editor.putSetting(
    key: String,
    value: Any,
): Boolean {
    when {
        value is Boolean -> putBoolean(key, value)
        value is Int -> putInt(key, value)
        value is Long -> putLong(key, value)
        value is Float -> putFloat(key, value)
        value is String -> putString(key, value)
        value is Set<*> && value.all { it is String } -> putStringSet(key, value.filterIsInstance<String>().toSet())
        else -> {
            Log.w(TAG, "Dropping $key: SharedPreferences cannot store ${value.javaClass.simpleName}")
            return false
        }
    }
    return true
}
