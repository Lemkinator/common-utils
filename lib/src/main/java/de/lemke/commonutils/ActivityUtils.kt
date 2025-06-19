@file:Suppress("unused")

package de.lemke.commonutils

import android.app.Activity
import android.text.SpannableString
import de.lemke.commonutils.ui.activity.AboutActivity
import de.lemke.commonutils.ui.activity.AboutMeActivity

private const val TAG = "ActivityUtils"

fun setupCommonActivities(
    appVersion: String,
    optionalText: String,
    onShareApp: (activity: Activity) -> Unit = {},
    cantOpenURLMessage: String? = null,
    noBrowserInstalledMessage: String? = null,
    noEmailAppInstalledText: String? = null,
) = setupCommonActivities(
    appVersion,
    SpannableString(optionalText),
    onShareApp,
    cantOpenURLMessage,
    noBrowserInstalledMessage,
    noEmailAppInstalledText,
)

fun setupCommonActivities(
    appVersion: String,
    optionalText: SpannableString,
    onShareApp: (activity: Activity) -> Unit = {},
    cantOpenURLMessage: String? = null,
    noBrowserInstalledMessage: String? = null,
    noEmailAppInstalledText: String? = null,
) {
    AboutActivity.Companion.apply {
        this.appVersion = appVersion
        this.optionalText = optionalText
    }
    AboutMeActivity.Companion.apply {
        this.onShareApp = onShareApp
        this.cantOpenURLMessage = cantOpenURLMessage
        this.noBrowserInstalledMessage = noBrowserInstalledMessage
        this.noEmailAppInstalledText = noEmailAppInstalledText
    }
}

fun setupAboutActivityWithGetVersion(
    getAppVersion: suspend () -> String,
    optionalText: String,
) {
    AboutActivity.Companion.apply {
        this.getAppVersion = getAppVersion
        this.optionalText = SpannableString(optionalText)
    }
}