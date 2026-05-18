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

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.preference.PreferenceManager
import de.lemke.commonutils.SaveLocation

/** Global singleton for accessing common-utils app settings; must be initialized via [initCommonUtilsSettingsAndSetDarkMode]. */
lateinit var commonUtilsSettings: SettingsRepository

/** SharedPreferences-backed repository for common-utils app settings. */
class SettingsRepository(
    preferences: SharedPreferences,
) {
    var darkMode: Boolean by preferences.delegates.darkMode(false)
    var autoDarkMode: Boolean by preferences.delegates.boolean(true)
    var lastVersionCode: Int by preferences.delegates.int(-1)
    var lastVersionName: String by preferences.delegates.string("0.0.0")
    var acceptedTosVersion: Int by preferences.delegates.int(-1)
    var devModeEnabled: Boolean by preferences.delegates.boolean(false)
    var search: String by preferences.delegates.string("")
    var imageSaveLocation: SaveLocation by preferences.delegates.saveLocation(SaveLocation.default)
}

/** Initializes [commonUtilsSettings] from the default shared preferences and applies the saved dark mode setting. */
fun Context.initCommonUtilsSettingsAndSetDarkMode() {
    commonUtilsSettings = SettingsRepository(PreferenceManager.getDefaultSharedPreferences(this))
    when {
        commonUtilsSettings.autoDarkMode -> setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
        commonUtilsSettings.darkMode -> setDefaultNightMode(MODE_NIGHT_YES)
        else -> setDefaultNightMode(MODE_NIGHT_NO)
    }
}
