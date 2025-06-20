@file:Suppress("unused")

package de.lemke.commonutils

import android.app.Activity
import android.text.SpannableString
import androidx.preference.PreferenceFragmentCompat
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity

private const val TAG = "ActivityUtils"

fun setupCommonUtilsSettingsActivity(vararg preferences: Int, initPreferences: suspend PreferenceFragmentCompat.() -> Unit = {}) {
    CommonUtilsSettingsActivity.Companion.preferences = preferences.toList()
    CommonUtilsSettingsActivity.Companion.initPreferences = initPreferences
}

fun setupCommonUtilsSettingsActivity(preferences: List<Int>, initPreferences: suspend PreferenceFragmentCompat.() -> Unit = {}) {
    CommonUtilsSettingsActivity.Companion.preferences = preferences
    CommonUtilsSettingsActivity.Companion.initPreferences = initPreferences
}

fun setupCommonUtilsAboutMeActivity(
    onShareApp: (activity: Activity) -> Unit = {},
    cantOpenURLMessage: String? = null,
    noBrowserInstalledMessage: String? = null,
    noEmailAppInstalledText: String? = null,
) {
    CommonUtilsAboutMeActivity.Companion.apply {
        this.onShareApp = onShareApp
        this.cantOpenURLMessage = cantOpenURLMessage
        this.noBrowserInstalledMessage = noBrowserInstalledMessage
        this.noEmailAppInstalledText = noEmailAppInstalledText
    }
}

fun setupCommonUtilsAboutActivity(appVersion: String, optionalText: String) =
    setupCommonUtilsAboutActivity(appVersion, SpannableString(optionalText))

fun setupCommonUtilsAboutActivity(appVersion: String, optionalText: SpannableString? = null) {
    CommonUtilsAboutActivity.Companion.apply {
        this.appVersion = appVersion
        this.optionalText = optionalText
    }
}

fun setupCommonUtilsAboutActivity(getAppVersion: suspend () -> String, optionalText: String) {
    CommonUtilsAboutActivity.Companion.apply {
        this.getAppVersion = getAppVersion
        this.optionalText = SpannableString(optionalText)
    }
}