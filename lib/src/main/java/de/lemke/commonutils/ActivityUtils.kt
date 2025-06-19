@file:Suppress("unused")

package de.lemke.commonutils

import android.app.Activity
import android.text.SpannableString
import de.lemke.commonutils.ui.activity.AboutActivity
import de.lemke.commonutils.ui.activity.AboutMeActivity

private const val TAG = "ActivityUtils"

fun setupCommonActivities(
    appName: String,
    appVersion: String,
    optionalText: String,
    email: String,
    devModeEnabled: Boolean,
    onDevModeChanged: suspend (Boolean) -> Unit = {},
    onShareApp: (activity: Activity) -> Unit = {},
    cantOpenURLMessage: String? = null,
    noBrowserInstalledMessage: String? = null,
    noEmailAppInstalledText: String? = null,
) = setupCommonActivities(
    appName,
    appVersion,
    SpannableString(optionalText),
    email,
    devModeEnabled,
    onDevModeChanged,
    onShareApp,
    cantOpenURLMessage,
    noBrowserInstalledMessage,
    noEmailAppInstalledText,
)

fun setupCommonActivities(
    appName: String,
    appVersion: String,
    optionalText: SpannableString,
    email: String,
    devModeEnabled: Boolean,
    onDevModeChanged: suspend (Boolean) -> Unit = {},
    onShareApp: (activity: Activity) -> Unit = {},
    cantOpenURLMessage: String? = null,
    noBrowserInstalledMessage: String? = null,
    noEmailAppInstalledText: String? = null,
) {
    AboutActivity.Companion.apply {
        this.appVersion = appVersion
        this.optionalText = optionalText
        this.devModeEnabled = devModeEnabled
        this.onDevModeChanged = onDevModeChanged
    }
    AboutMeActivity.Companion.apply {
        this.appName = appName
        this.email = email
        this.onShareApp = onShareApp
        this.cantOpenURLMessage = cantOpenURLMessage
        this.noBrowserInstalledMessage = noBrowserInstalledMessage
        this.noEmailAppInstalledText = noEmailAppInstalledText
    }
}

fun setupAboutActivityWithGetVersion(
    getAppVersion: suspend () -> String,
    optionalText: String,
    devModeEnabled: Boolean,
    onDevModeChanged: suspend (Boolean) -> Unit = {},
) {
    AboutActivity.Companion.apply {
        this.getAppVersion = getAppVersion
        this.optionalText = SpannableString(optionalText)
        this.devModeEnabled = devModeEnabled
        this.onDevModeChanged = onDevModeChanged
    }
}