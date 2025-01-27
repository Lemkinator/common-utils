@file:Suppress("unused")

package de.lemke.commonutils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment

private const val TAG = "OpenUtils"

fun Fragment.openURL(url: String?, cantOpenURLMessage: String? = null, noBrowserInstalledMessage: String? = null) =
    requireContext().openURL(url, cantOpenURLMessage, noBrowserInstalledMessage)

fun Context.openURL(url: String?, cantOpenURLMessage: String? = null, noBrowserInstalledMessage: String? = null) {
    try {
        if (url.isNullOrBlank()) {
            Log.e(TAG, "link is null or blank")
            toast(cantOpenURLMessage ?: getString(R.string.error_cant_open_url))
        } else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
        toast(noBrowserInstalledMessage ?: getString(R.string.no_browser_app_installed))
    } catch (e: Exception) {
        e.printStackTrace()
        toast(cantOpenURLMessage ?: getString(R.string.error_cant_open_url))
    }
}

fun Fragment.openApp(packageName: String, tryLocalFirst: Boolean) = requireContext().openApp(packageName, tryLocalFirst)

fun Context.openApp(packageName: String, tryLocalFirst: Boolean) {
    if (tryLocalFirst) openAppWithPackageName(packageName)
    else openAppWithPackageNameOnStore(packageName)
}

private fun Context.openAppWithPackageName(packageName: String) = try {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    else openAppWithPackageNameOnStore(packageName)
} catch (e: Exception) {
    e.printStackTrace()
    toast(getString(R.string.error_cant_open_app))
}

private fun Context.openAppWithPackageNameOnStore(packageName: String) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse(getString(R.string.playstore_app_link) + packageName)
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (anfe: ActivityNotFoundException) {
        anfe.printStackTrace()
        intent.data = Uri.parse(getString(R.string.playstore_link) + packageName)
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(R.string.error_cant_open_app))
        }
    }
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
fun areAppLocalSettingsSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Fragment.openAppLocaleSettings(notSupportedMessage: String? = null): Boolean {
    if (!areAppLocalSettingsSupported()) {
        toast(notSupportedMessage ?: getString(R.string.change_language_not_supported_by_device))
        return false
    }
    try {
        startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
        toast(notSupportedMessage ?: getString(R.string.change_language_not_supported_by_device))
    }
    return true
}

fun Context.openApplicationSettings() = try {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
} catch (e: Exception) {
    e.printStackTrace()
    toast(R.string.error_cant_open_app_settings)
}

