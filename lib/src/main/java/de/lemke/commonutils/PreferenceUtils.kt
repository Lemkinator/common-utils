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
package de.lemke.commonutils

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import de.lemke.commonutils.data.commonUtilsSettings
import dev.oneuiproject.oneui.ktx.addRelativeLinksCard
import dev.oneuiproject.oneui.ktx.onClick
import dev.oneuiproject.oneui.ktx.onNewValue
import dev.oneuiproject.oneui.ktx.setOnClickListenerWithProgress
import dev.oneuiproject.oneui.preference.HorizontalRadioPreference
import dev.oneuiproject.oneui.widget.RelativeLink
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.oneuiproject.oneui.design.R as designR

private const val TAG = "PreferenceUtils"
private const val DELETE_APP_DATA_DELAY_MS = 500L

/** Adds a relative-links card with "Share app" and "Rate app" actions to the preference screen. */
@NoCoverage
fun PreferenceFragmentCompat.addShareAppAndRateRelativeLinksCard() {
    addRelativeLinksCard(
        RelativeLink(getString(R.string.commonutils_share_app)) { shareApp() },
        RelativeLink(getString(R.string.commonutils_rate_app)) { openApp(requireContext().packageName, false) },
    )
}

/** Initializes the standard common-utils preferences (dark mode, save location, language, dev options, more info). */
fun PreferenceFragmentCompat.initCommonUtilsPreferences() {
    initDarkMode()
    initImageSaveLocation()
    initMoreInfo()
    findPreference<PreferenceCategory>(getString(R.string.commonutils_preference_key_dev_options))?.apply {
        isVisible = commonUtilsSettings.devModeEnabled
    } ?: Log.w(TAG, "dev options preference category is null, skipping initialization")

    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_delete_app_data))?.apply {
        onClick { deleteAppDataAndExit() }
    } ?: Log.w(TAG, "delete app data preference is null, skipping initialization")

    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_language))?.apply {
        if (SDK_INT >= VERSION_CODES.TIRAMISU) {
            isVisible = true
            onClick { openAppLocaleSettings() }
        }
    } ?: Log.w(TAG, "language preference is null, skipping initialization")
}

private fun PreferenceFragmentCompat.initMoreInfo() {
    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_privacy_policy))?.apply {
        onClick { openURL(getString(R.string.commonutils_privacy_website)) }
    } ?: Log.w(TAG, "privacy preference is null, skipping initialization")
    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_tos))?.apply {
        onClick {
            AlertDialog
                .Builder(requireContext())
                .setTitle(getString(R.string.commonutils_tos))
                .setMessage(getString(R.string.commonutils_tos_content))
                .setPositiveButton(R.string.commonutils_ok, null)
                .show()
        }
    } ?: Log.w(TAG, "tos preference is null, skipping initialization")
    findPreference<PreferenceScreen>(getString(R.string.commonutils_preference_key_report_bug))?.apply {
        onClick {
            sendEmailBugReport(
                getString(R.string.commonutils_email),
                requireContext().applicationInfo.loadLabel(requireContext().packageManager).toString(),
            )
        }
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

/** Shows a confirmation dialog and clears all application user data on confirmation. */
@NoCoverage
fun Fragment.deleteAppDataAndExit(
    title: String? = null,
    message: String? = null,
    cancel: String? = null,
    delete: String? = null,
) {
    val dialog =
        AlertDialog
            .Builder(requireContext())
            .setTitle(title ?: getString(R.string.commonutils_delete_appdata_and_exit))
            .setMessage(message ?: getString(R.string.commonutils_delete_appdata_and_exit_warning))
            .setNegativeButton(cancel ?: getString(designR.string.oui_des_common_cancel), null)
            .setPositiveButton(delete ?: getString(R.string.commonutils_delete), null)
            .create()
    dialog.show()
    dialog.getButton(BUTTON_POSITIVE).apply {
        setTextColor(requireContext().getColor(designR.color.oui_des_functional_red_color))
        setOnClickListenerWithProgress { _, _ ->
            lifecycleScope.launch {
                delay(DELETE_APP_DATA_DELAY_MS.milliseconds)
                (context?.getSystemService(ACTIVITY_SERVICE) as? ActivityManager)?.clearApplicationUserData()
            }
        }
    }
}

@NoCoverage
private fun PreferenceFragmentCompat.initDarkMode() {
    val darkModePref = findPreference<HorizontalRadioPreference>(getString(R.string.commonutils_preference_key_dark_mode))
    val autoDarkModePref = findPreference<SwitchPreferenceCompat>(getString(R.string.commonutils_preference_key_auto_dark_mode))
    if (autoDarkModePref == null || darkModePref == null) {
        Log.e(TAG, "autoDarkModePref or darkModePref is null, skipping initialization")
    } else {
        darkModePref.isEnabled = !commonUtilsSettings.autoDarkMode
        darkModePref.value = if (commonUtilsSettings.darkMode) "1" else "0"
        darkModePref.setDividerEnabled(false)
        darkModePref.setTouchEffectEnabled(false)
        autoDarkModePref.isChecked = commonUtilsSettings.autoDarkMode
        autoDarkModePref.onNewValue {
            darkModePref.isEnabled = !it
            if (it) {
                setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
            } else {
                if (commonUtilsSettings.darkMode) {
                    setDefaultNightMode(MODE_NIGHT_YES)
                } else {
                    setDefaultNightMode(MODE_NIGHT_NO)
                }
            }
        }
        darkModePref.onNewValue {
            commonUtilsSettings.darkMode = it == "1"
            setDefaultNightMode(if (it == "1") MODE_NIGHT_YES else MODE_NIGHT_NO)
        }
    }
}
