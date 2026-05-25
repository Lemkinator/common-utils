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

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import de.lemke.commonutils.AppStartResult.FIRST_TIME
import de.lemke.commonutils.AppStartResult.FIRST_TIME_VERSION
import de.lemke.commonutils.AppStartResult.NORMAL
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity

private const val TAG = "CheckAppStartUtils"

/** Checks whether this is the first run, a version upgrade, or a normal start; persists version info for next launch. */
fun AppCompatActivity.checkAppStart(
    versionCode: Int,
    versionName: String,
    versionCodeThreshold: Int = -1,
): AppStart {
    val lastVersionCode = commonUtilsSettings.lastVersionCode
    val lastVersionName = commonUtilsSettings.lastVersionName
    commonUtilsSettings.lastVersionCode = versionCode
    commonUtilsSettings.lastVersionName = versionName
    val tosVersion = resources.getInteger(R.integer.commonutils_tos_version)
    val acceptedTosVersion = commonUtilsSettings.acceptedTosVersion
    val result =
        when {
            lastVersionCode == -1 -> {
                FIRST_TIME
            }

            lastVersionCode < versionCode -> {
                FIRST_TIME_VERSION
            }

            lastVersionCode > versionCode -> {
                Log.w(TAG, "Current version code ($versionCode) is less then the one recognized on last startup ($lastVersionCode). ")
                Log.w(TAG, "Defensively assuming normal app start.")
                NORMAL
            }

            else -> {
                NORMAL
            }
        }
    return AppStart(result, versionCode, versionName, lastVersionCode, lastVersionName, tosVersion, acceptedTosVersion).apply {
        Log.d(TAG, this.toString())
        if (result == FIRST_TIME_VERSION && !tosAccepted) {
            CommonUtilsOOBEActivity.tosChanged = true
        }
    }
}

/**
 * Checks the app start result and opens the OOBE activity if required.
 *
 * @return `true` if OOBE was opened and the current activity finished.
 */
fun AppCompatActivity.checkAppStartAndHandleOOBE(
    versionCode: Int,
    versionName: String,
    versionCodeThreshold: Int = -1,
): Boolean {
    if (checkAppStart(versionCode, versionName, versionCodeThreshold).shouldShowOOBE) {
        openOOBEAndFinish()
        return true
    }
    return false
}

/** Starts [CommonUtilsOOBEActivity] and finishes this activity with a fade transition. */
fun AppCompatActivity.openOOBEAndFinish() {
    startActivity(Intent(applicationContext, CommonUtilsOOBEActivity::class.java))
    @Suppress("DEPRECATION")
    if (SDK_INT < UPSIDE_DOWN_CAKE) overridePendingTransition(fade_in, fade_out)
    finishAfterTransition()
}

/** Result category of an app launch relative to the previously recorded version. */
enum class AppStartResult { FIRST_TIME, FIRST_TIME_VERSION, NORMAL }

/** Snapshot of version and TOS state captured at app launch. */
class AppStart(
    val result: AppStartResult,
    val versionCode: Int,
    val versionName: String,
    val lastVersionCode: Int,
    val lastVersionName: String,
    val tosVersion: Int,
    val acceptedTosVersion: Int,
) {
    /** `true` if no previous install was recorded. */
    val isFirstTime get() = lastVersionCode == -1

    /** `true` if the app was upgraded since the last launch. */
    val isFirstTimeVersion get() = lastVersionCode < versionCode

    /** `true` if the user has accepted the current TOS version. */
    val tosAccepted get() = acceptedTosVersion >= tosVersion

    /** `true` if OOBE should be shown (first install or TOS not accepted). */
    val shouldShowOOBE get() = isFirstTime || !tosAccepted

    /** `true` if [threshold] falls within the range of version codes updated across on this launch. */
    fun versionThresholdPassed(threshold: Int) = threshold in lastVersionCode..<versionCode

    override fun toString(): String =
        "AppStart(result=$result, versionCode=$versionCode, versionName='$versionName', " +
            "lastVersionCode=$lastVersionCode, lastVersionName='$lastVersionName', " +
            "tosVersion=$tosVersion, acceptedTosVersion=$acceptedTosVersion)"
}
