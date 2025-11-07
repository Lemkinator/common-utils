@file:Suppress("unused")

package de.lemke.commonutils

import android.app.Activity
import android.text.SpannableString
import androidx.preference.PreferenceFragmentCompat
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity

private const val TAG = "ActivityUtils"

fun setupCommonUtilsOOBEActivity(setAcceptedTosVersion: Boolean? = null, nextActivity: Class<*>) {
    setAcceptedTosVersion?.let { CommonUtilsOOBEActivity.setAcceptedTosVersion = it }
    CommonUtilsOOBEActivity.nextActivity = nextActivity
}

fun setupCommonUtilsOOBEActivity(setAcceptedTosVersion: Boolean? = null, onContinue: (() -> Unit)) {
    setAcceptedTosVersion?.let { CommonUtilsOOBEActivity.setAcceptedTosVersion = it }
    CommonUtilsOOBEActivity.onContinue = onContinue
}

fun setupCommonUtilsSettingsActivity(vararg preferences: Int, initPreferences: suspend PreferenceFragmentCompat.() -> Unit = {}) {
    CommonUtilsSettingsActivity.preferences = preferences.toList()
    CommonUtilsSettingsActivity.initPreferences = initPreferences
}

fun setupCommonUtilsSettingsActivity(preferences: List<Int>, initPreferences: suspend PreferenceFragmentCompat.() -> Unit = {}) {
    CommonUtilsSettingsActivity.preferences = preferences
    CommonUtilsSettingsActivity.initPreferences = initPreferences
}

fun setupCommonUtilsAboutMeActivity(onShareApp: (activity: Activity) -> Unit = {}) {
    CommonUtilsAboutMeActivity.apply {
        this.onShareApp = onShareApp
    }
}

fun setupCommonUtilsAboutActivity(appVersion: String, optionalText: SpannableString? = null) {
    CommonUtilsAboutActivity.apply {
        this.appVersion = appVersion
        this.optionalText = optionalText
    }
}

fun setupCommonUtilsAboutActivity(getAppVersion: suspend () -> String) {
    CommonUtilsAboutActivity.getAppVersion = getAppVersion
}