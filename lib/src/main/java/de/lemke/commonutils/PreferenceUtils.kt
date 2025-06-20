@file:Suppress("unused")

package de.lemke.commonutils

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import de.lemke.commonutils.data.commonUtilsSettings
import dev.oneuiproject.oneui.ktx.addRelativeLinksCard
import dev.oneuiproject.oneui.preference.HorizontalRadioPreference
import dev.oneuiproject.oneui.widget.RelativeLink

private const val TAG = "PreferenceUtils"

fun Preference.onPrefClick(function: (Preference) -> Unit) {
    onPreferenceClickListener = OnPreferenceClickListener { preference ->
        function(preference)
        true
    }
}

fun Preference.onPrefClickWithResult(function: (Preference) -> Boolean) {
    onPreferenceClickListener = OnPreferenceClickListener { preference ->
        function(preference)
    }
}

fun Preference.onPrefChange(function: (Preference, Any) -> Boolean) {
    onPreferenceChangeListener = object : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            return function(preference, newValue)
        }
    }
}

fun Preference.onPrefChange(function: () -> Unit) {
    onPreferenceChangeListener = object : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            function()
            return true
        }
    }
}

inline fun <reified T> Preference.onPrefChange(crossinline function: (T) -> Unit) {
    onPreferenceChangeListener = object : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            if (newValue is T) {
                function(newValue)
                return true
            }
            Log.e("OnPrefChange", "Expected type ${T::class.java.simpleName}, but got ${newValue::class.java.simpleName}")
            return false
        }
    }
}

fun PreferenceFragmentCompat.addShareAppAndRateRelativeLinksCard() {
    addRelativeLinksCard(
        RelativeLink(getString(R.string.commonutils_share_app)) { shareApp() },
        RelativeLink(getString(R.string.commonutils_rate_app)) { openApp(requireContext().packageName, false) }
    )
}

fun PreferenceFragmentCompat.initCommonUtilsPreferences() {
    initDarkMode()
    initImageSaveLocation()
    initMoreInfo()
    findPreference<PreferenceCategory>(getString(R.string.commonutils_preference_key_dev_options))?.apply {
        isVisible = commonUtilsSettings.devModeEnabled
    } ?: Log.w(TAG, "dev options preference category is null, skipping initialization")

    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_delete_app_data))?.apply {
        onPrefClick { deleteAppDataAndExit() }
    } ?: Log.w(TAG, "delete app data preference is null, skipping initialization")

    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_language))?.apply {
        if (SDK_INT >= VERSION_CODES.TIRAMISU) {
            isVisible = true
            onPrefClickWithResult { openAppLocaleSettings() }
        }
    } ?: Log.w(TAG, "language preference is null, skipping initialization")
}

private fun PreferenceFragmentCompat.initMoreInfo() {
    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_privacy_policy))?.apply {
        onPrefClickWithResult { openURL(getString(R.string.commonutils_privacy_website)) }
    } ?: Log.w(TAG, "privacy preference is null, skipping initialization")
    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_tos))?.apply {
        onPrefClick {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.commonutils_tos))
                .setMessage(getString(R.string.commonutils_tos_content))
                .setPositiveButton(R.string.commonutils_ok, null)
                .show()
        }
    } ?: Log.w(TAG, "tos preference is null, skipping initialization")
    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_report_bug))?.apply {
        onPrefClickWithResult { sendEmailBugReport(getString(R.string.commonutils_email), getString(R.string.commonutils_app_name)) }
    } ?: Log.w(TAG, "report bug preference is null, skipping initialization")
}

private fun PreferenceFragmentCompat.initImageSaveLocation() {
    findPreference<DropDownPreference>(getString(R.string.commonutils_preference_key_image_save_location))?.apply {
        entries = SaveLocation.getLocalizedEntries(this@initImageSaveLocation.requireContext())
        entryValues = SaveLocation.entryValues
        if (SDK_INT <= VERSION_CODES.Q) {
            value = SaveLocation.CUSTOM.name
            isEnabled = false
        }
    } ?: Log.w(TAG, "imageSaveLocation preference is null, skipping initialization")
}

private fun PreferenceFragmentCompat.initDarkMode() {
    val darkModePref = findPreference<HorizontalRadioPreference>(getString(R.string.commonutils_preference_key_dark_mode))
    val autoDarkModePref = findPreference<SwitchPreferenceCompat>(getString(R.string.commonutils_preference_key_auto_dark_mode))
    if (autoDarkModePref == null || darkModePref == null) Log.e(TAG, "autoDarkModePref or darkModePref is null, skipping initialization")
    else {
        darkModePref.isEnabled = !commonUtilsSettings.autoDarkMode
        darkModePref.value = if (commonUtilsSettings.darkMode) "1" else "0"
        darkModePref.setDividerEnabled(false)
        darkModePref.setTouchEffectEnabled(false)
        autoDarkModePref.isChecked = commonUtilsSettings.autoDarkMode
        autoDarkModePref.onPrefChange { newValue: Boolean ->
            darkModePref.isEnabled = !newValue
            if (newValue) setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
            else {
                if (commonUtilsSettings.darkMode) setDefaultNightMode(MODE_NIGHT_YES)
                else setDefaultNightMode(MODE_NIGHT_NO)
            }
        }
        darkModePref.onPrefChange { newValue: String ->
            commonUtilsSettings.darkMode = newValue == "1"
            setDefaultNightMode(if (newValue == "1") MODE_NIGHT_YES else MODE_NIGHT_NO)
        }
    }
}


