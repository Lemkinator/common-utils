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

lateinit var commonUtilsSettings: SettingsRepository

class SettingsRepository(preferences: SharedPreferences) {
    var darkMode: Boolean by preferences.delegates.darkMode(false)
    var autoDarkMode: Boolean by preferences.delegates.boolean(true)
    var lastVersionCode: Int by preferences.delegates.int(-1)
    var lastVersionName: String by preferences.delegates.string("0.0.0")
    var acceptedTosVersion: Int by preferences.delegates.int(-1)
    var devModeEnabled: Boolean by preferences.delegates.boolean(false)
    var search: String by preferences.delegates.string("")
    var imageSaveLocation: SaveLocation by preferences.delegates.saveLocation(SaveLocation.default)
}

fun Context.initCommonUtilsSettingsAndSetDarkMode() {
    commonUtilsSettings = SettingsRepository(PreferenceManager.getDefaultSharedPreferences(this))
    when {
        commonUtilsSettings.autoDarkMode -> setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
        commonUtilsSettings.darkMode -> setDefaultNightMode(MODE_NIGHT_YES)
        else -> setDefaultNightMode(MODE_NIGHT_NO)
    }
}
