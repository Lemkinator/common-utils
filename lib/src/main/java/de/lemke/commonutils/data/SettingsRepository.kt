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
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/** SharedPreferences-backed repository for common-utils app settings; open so apps can add their own fields via subclassing. */
open class SettingsRepository(
    protected val preferences: SharedPreferences,
) {
    /** Whether dark mode is explicitly enabled (stored as `"1"`/`"0"` for legacy `HorizontalRadioPreference` compatibility). */
    var darkMode: Boolean by preferences.delegates.darkMode(false)

    /** Whether to follow the system dark mode setting instead of the explicit [darkMode] value. */
    var autoDarkMode: Boolean by preferences.delegates.boolean(true)

    /** The version code recorded on the previous app launch, or -1 if never set. */
    var lastVersionCode: Int by preferences.delegates.int(-1)

    /** The version name recorded on the previous app launch. */
    var lastVersionName: String by preferences.delegates.string("0.0.0")

    /** The highest TOS version the user has accepted, or -1 if the user has never accepted. */
    var acceptedTosVersion: Int by preferences.delegates.int(-1)

    /** Whether developer mode is currently enabled. */
    var devModeEnabled: Boolean by preferences.delegates.boolean(false)

    /** The last search query entered by the user. */
    var search: String by preferences.delegates.string("")

    /** The preferred location for exported images. */
    var imageSaveLocation: SaveLocation by preferences.delegates.saveLocation(SaveLocation.default)

    /**
     * Builds a StateFlow snapshot, backed by one OnSharedPreferenceChangeListener scoped to [scope] — use an
     * app-lifetime scope (e.g. Hilt `@ApplicationScope`), never an Activity/ViewModel scope, or the listener leaks
     * that scope's lifetime with it.
     */
    protected fun <S> settingsFlow(
        scope: CoroutineScope,
        snapshot: () -> S,
    ): StateFlow<S> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot()) }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            trySend(snapshot())
            awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, snapshot())
}

/** Applies this [SettingsRepository]'s dark mode setting to the app's default night mode. */
fun SettingsRepository.applyDarkMode() {
    when {
        autoDarkMode -> setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
        darkMode -> setDefaultNightMode(MODE_NIGHT_YES)
        else -> setDefaultNightMode(MODE_NIGHT_NO)
    }
}
