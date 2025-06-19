@file:Suppress("unused")

package de.lemke.commonutils

import android.app.Activity
import android.text.SpannableString
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity

private const val TAG = "ActivityUtils"

fun setupCommonUtilsSettingsActivity(preferences: List<Int>) {
    CommonUtilsSettingsActivity.Companion.preferences = preferences
}

fun setupAboutMeActivity(
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

fun setupAboutActivity(appVersion: String, optionalText: String) = setupAboutActivity(appVersion, SpannableString(optionalText))
fun setupAboutActivity(appVersion: String, optionalText: SpannableString? = null) {
    CommonUtilsAboutActivity.Companion.apply {
        this.appVersion = appVersion
        this.optionalText = optionalText
    }
}

fun setupAboutActivity(getAppVersion: suspend () -> String, optionalText: String) {
    CommonUtilsAboutActivity.Companion.apply {
        this.getAppVersion = getAppVersion
        this.optionalText = SpannableString(optionalText)
    }
}