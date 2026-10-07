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
package de.lemke.commonutils.ui.utils

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
import de.lemke.commonutils.R

private const val TAG = "OpenUtils"

/**
 * Opens the app with [packageName], trying the local install first if [tryLocalFirst] is `true`, otherwise opens the
 * Play Store; a failure shows the error toast.
 */
fun Fragment.openApp(
    packageName: String,
    tryLocalFirst: Boolean,
): LaunchOutcome = requireContext().openApp(packageName, tryLocalFirst)

/**
 * Opens the app with [packageName], trying the local install first if [tryLocalFirst] is `true`, otherwise opens the
 * Play Store; a failure shows the error toast.
 */
fun Context.openApp(
    packageName: String,
    tryLocalFirst: Boolean,
): LaunchOutcome =
    if (tryLocalFirst) {
        openAppWithPackageName(packageName)
    } else {
        openAppWithPackageNameOnStore(packageName)
    }

private fun Context.openAppWithPackageName(packageName: String): LaunchOutcome =
    try {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            launchForOutcome { startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK)) }
        } else {
            openAppWithPackageNameOnStore(packageName)
        }
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "Failed to open app with package name", e)
        toast(getString(R.string.commonutils_error_cant_open_app))
        LaunchOutcome.Failed
    }

private fun Context.openAppWithPackageNameOnStore(packageName: String): LaunchOutcome {
    val uris =
        listOf(
            (getString(R.string.commonutils_playstore_app_link) + packageName).toUri(),
            (getString(R.string.commonutils_playstore_link) + packageName).toUri(),
        )
    val intent = Intent(ACTION_VIEW).addFlags(FLAG_ACTIVITY_NEW_TASK)
    for (uri in uris) {
        try {
            return launchForOutcome { startActivity(intent.apply { data = uri }) }
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Failed to open Play Store: $uri", e)
        }
    }
    toast(getString(R.string.commonutils_error_cant_open_app))
    return LaunchOutcome.Failed
}

/** Returns `true` if per-app language settings are supported (Android 13+). */
@ChecksSdkIntAtLeast(api = TIRAMISU)
fun areAppLocalSettingsSupported(): Boolean = SDK_INT >= TIRAMISU

/** Opens the system per-app language settings screen for this app; a failure shows the error toast. */
@RequiresApi(TIRAMISU)
fun Fragment.openAppLocaleSettings(): LaunchOutcome {
    if (!areAppLocalSettingsSupported()) {
        toast(getString(R.string.commonutils_change_language_not_supported_by_device))
        return LaunchOutcome.Failed
    }
    return try {
        requireContext().launchForOutcome {
            startActivity(Intent(ACTION_APP_LOCALE_SETTINGS, "package:${requireContext().packageName}".toUri()))
        }
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "App locale settings not available", e)
        toast(getString(R.string.commonutils_change_language_not_supported_by_device))
        LaunchOutcome.Failed
    }
}

/** Opens the system application settings screen for this app; a failure shows the error toast. */
fun Context.openApplicationSettings(): LaunchOutcome =
    try {
        launchForOutcome {
            startActivity(
                Intent(ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
                    .setFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "Failed to open application settings", e)
        toast(R.string.commonutils_error_cant_open_app_settings)
        LaunchOutcome.Failed
    }
