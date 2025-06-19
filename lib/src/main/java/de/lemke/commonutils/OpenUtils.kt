@file:Suppress("unused")

package de.lemke.commonutils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.provider.Settings.ACTION_APP_LOCALE_SETTINGS
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.fragment.app.Fragment

private const val TAG = "OpenUtils"

fun Fragment.openURL(url: String?, cantOpenURLMessage: String? = null, noBrowserInstalledMessage: String? = null): Boolean =
    requireContext().openURL(url, cantOpenURLMessage, noBrowserInstalledMessage)

fun Context.openURL(url: String?, cantOpenURLMessage: String? = null, noBrowserInstalledMessage: String? = null): Boolean = try {
    if (url.isNullOrBlank()) {
        Log.e(TAG, "link is null or blank")
        toast(cantOpenURLMessage ?: getString(R.string.commonutils_error_cant_open_url))
        false
    } else {
        startActivity(Intent(ACTION_VIEW, url.toUri()))
        true
    }
} catch (e: ActivityNotFoundException) {
    e.printStackTrace()
    toast(noBrowserInstalledMessage ?: getString(R.string.commonutils_no_browser_app_installed))
    false
} catch (e: Exception) {
    e.printStackTrace()
    toast(cantOpenURLMessage ?: getString(R.string.commonutils_error_cant_open_url))
    false
}

fun Fragment.openApp(packageName: String, tryLocalFirst: Boolean): Boolean = requireContext().openApp(packageName, tryLocalFirst)

fun Context.openApp(packageName: String, tryLocalFirst: Boolean): Boolean =
    if (tryLocalFirst) openAppWithPackageName(packageName)
    else openAppWithPackageNameOnStore(packageName)

private fun Context.openAppWithPackageName(packageName: String): Boolean = try {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK))
        true
    } else openAppWithPackageNameOnStore(packageName)
} catch (e: Exception) {
    e.printStackTrace()
    toast(getString(R.string.commonutils_error_cant_open_app))
    false
}

private fun Context.openAppWithPackageNameOnStore(packageName: String): Boolean {
    val intent = Intent(ACTION_VIEW)
    intent.data = (getString(R.string.commonutils_playstore_app_link) + packageName).toUri()
    try {
        startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK))
        return true
    } catch (anfe: ActivityNotFoundException) {
        anfe.printStackTrace()
        intent.data = (getString(R.string.commonutils_playstore_link) + packageName).toUri()
        try {
            startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(R.string.commonutils_error_cant_open_app))
            return false
        }
    }
}

@ChecksSdkIntAtLeast(api = TIRAMISU)
fun areAppLocalSettingsSupported(): Boolean = SDK_INT >= TIRAMISU

@RequiresApi(TIRAMISU)
fun Fragment.openAppLocaleSettings(notSupportedMessage: String? = null): Boolean {
    if (!areAppLocalSettingsSupported()) {
        toast(notSupportedMessage ?: getString(R.string.commonutils_change_language_not_supported_by_device))
        return false
    }
    try {
        startActivity(Intent(ACTION_APP_LOCALE_SETTINGS, "package:${requireContext().packageName}".toUri()))
        return true
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
        toast(notSupportedMessage ?: getString(R.string.commonutils_change_language_not_supported_by_device))
        return false
    }
}

fun Context.openApplicationSettings(): Boolean = try {
    startActivity(
        Intent(ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
            .setFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
    )
    true
} catch (e: Exception) {
    e.printStackTrace()
    toast(R.string.commonutils_error_cant_open_app_settings)
    false
}

