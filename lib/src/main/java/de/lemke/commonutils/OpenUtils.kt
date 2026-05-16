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

fun Fragment.openApp(
    packageName: String,
    tryLocalFirst: Boolean,
): Boolean = requireContext().openApp(packageName, tryLocalFirst)

fun Context.openApp(
    packageName: String,
    tryLocalFirst: Boolean,
): Boolean =
    if (tryLocalFirst) {
        openAppWithPackageName(packageName)
    } else {
        openAppWithPackageNameOnStore(packageName)
    }

private fun Context.openAppWithPackageName(packageName: String): Boolean =
    try {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK))
            true
        } else {
            openAppWithPackageNameOnStore(packageName)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open app with package name", e)
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
        Log.e(TAG, "Failed to open Play Store app link", anfe)
        intent.data = (getString(R.string.commonutils_playstore_link) + packageName).toUri()
        try {
            startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Play Store link", e)
            toast(getString(R.string.commonutils_error_cant_open_app))
            return false
        }
    }
}

@ChecksSdkIntAtLeast(api = TIRAMISU)
fun areAppLocalSettingsSupported(): Boolean = SDK_INT >= TIRAMISU

@RequiresApi(TIRAMISU)
fun Fragment.openAppLocaleSettings(): Boolean {
    if (!areAppLocalSettingsSupported()) {
        toast(getString(R.string.commonutils_change_language_not_supported_by_device))
        return false
    }
    try {
        startActivity(Intent(ACTION_APP_LOCALE_SETTINGS, "package:${requireContext().packageName}".toUri()))
        return true
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "App locale settings not available", e)
        toast(getString(R.string.commonutils_change_language_not_supported_by_device))
        return false
    }
}

fun Context.openApplicationSettings(): Boolean =
    try {
        startActivity(
            Intent(ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
                .setFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK),
        )
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open application settings", e)
        toast(R.string.commonutils_error_cant_open_app_settings)
        false
    }
