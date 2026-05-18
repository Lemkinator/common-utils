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
@file:Suppress("unused")

package de.lemke.commonutils.data

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import de.lemke.commonutils.SaveLocation
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Advanced delegation, allowing for custom keys and defaults, and type safety. A little more verbose at the usage site.
 * https://www.youtube.com/watch?v=KFgb6l1PUJI&t=600s
 */
val SharedPreferences.delegates get() = SharedPreferenceDelegates(this)

/** Factory for type-safe [ReadWriteProperty] delegates backed by [SharedPreferences]. */
@SuppressLint("CommitPrefEdits")
class SharedPreferenceDelegates(
    private val prefs: SharedPreferences,
) {
    /** Delegate that reads/writes a [Boolean] preference. */
    fun boolean(
        default: Boolean = false,
        key: String? = null,
    ): ReadWriteProperty<Any, Boolean> = create(default, key, prefs::getBoolean, prefs.edit()::putBoolean)

    /** Delegate that reads/writes an [Int] preference. */
    fun int(
        default: Int = 0,
        key: String? = null,
    ): ReadWriteProperty<Any, Int> = create(default, key, prefs::getInt, prefs.edit()::putInt)

    /** Delegate that reads/writes a [Float] preference. */
    fun float(
        default: Float = 0f,
        key: String? = null,
    ): ReadWriteProperty<Any, Float> = create(default, key, prefs::getFloat, prefs.edit()::putFloat)

    /** Delegate that reads/writes a [Long] preference. */
    fun long(
        default: Long = 0L,
        key: String? = null,
    ): ReadWriteProperty<Any, Long> = create(default, key, prefs::getLong, prefs.edit()::putLong)

    /** Delegate that reads/writes a [String] preference. */
    fun string(
        default: String = "",
        key: String? = null,
    ): ReadWriteProperty<Any, String> = create(default, key, { k, d -> prefs.getString(k, d) ?: d }, prefs.edit()::putString)

    /** Delegate that reads/writes a [Set]<[String]> preference. */
    fun stringSet(
        default: Set<String> = emptySet(),
        key: String? = null,
    ): ReadWriteProperty<Any, Set<String>> = create(default, key, { k, d -> prefs.getStringSet(k, d) ?: d }, prefs.edit()::putStringSet)

    /** Delegate that reads/writes a dark mode flag stored as `"1"`/`"0"` for legacy HorizontalRadioPreference compatibility. */
    fun darkMode(
        default: Boolean = false,
        key: String? = null,
    ): ReadWriteProperty<Any, Boolean> =
        create(
            default,
            key,
            { k, d -> prefs.getString(k, if (d) "1" else "0") == "1" },
            { k, v -> prefs.edit().putString(k, if (v) "1" else "0") },
        )

    /** Delegate that reads/writes a [SaveLocation] preference, forcing [SaveLocation.CUSTOM] on API ≤29. */
    fun saveLocation(
        default: SaveLocation = SaveLocation.default,
        key: String? = null,
    ): ReadWriteProperty<Any, SaveLocation> =
        create(
            default,
            key,
            { k, d ->
                if (SDK_INT <= VERSION_CODES.Q) {
                    SaveLocation.CUSTOM
                } else {
                    SaveLocation.fromStringOrDefault(prefs.getString(k, d.name))
                }
            },
            { k, v -> prefs.edit().putString(k, if (SDK_INT <= VERSION_CODES.Q) SaveLocation.CUSTOM.name else v.name) },
        )

    private fun <T> create(
        default: T,
        key: String? = null,
        getter: (key: String, default: T) -> T,
        setter: (key: String, value: T) -> SharedPreferences.Editor,
    ) = object : ReadWriteProperty<Any, T> {
        private fun key(property: KProperty<*>) = key ?: property.name

        override fun getValue(
            thisRef: Any,
            property: KProperty<*>,
        ): T = getter(key(property), default)

        override fun setValue(
            thisRef: Any,
            property: KProperty<*>,
            value: T,
        ) = setter(key(property), value).apply()
    }
}
