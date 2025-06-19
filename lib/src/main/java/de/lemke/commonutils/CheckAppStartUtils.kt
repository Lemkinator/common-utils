@file:Suppress("unused")

package de.lemke.commonutils

import android.util.Log
import de.lemke.commonutils.AppStart.FIRST_TIME
import de.lemke.commonutils.AppStart.FIRST_TIME_VERSION
import de.lemke.commonutils.AppStart.NORMAL
import de.lemke.commonutils.data.commonUtilsSettings

private const val TAG = "CheckAppStartUtils"

fun checkAppStart(versionCode: Int, versionName: String): AppStart {
    val lastVersionCode = commonUtilsSettings.lastVersionCode
    val lastVersionName = commonUtilsSettings.lastVersionName
    commonUtilsSettings.lastVersionCode = versionCode
    commonUtilsSettings.lastVersionName = versionName
    Log.d(TAG, "Current version code: $versionCode , last version code: $lastVersionCode")
    Log.d(TAG, "Current version name: $versionName , last version name: $lastVersionName")
    return when {
        lastVersionCode == -1 -> FIRST_TIME
        lastVersionCode < versionCode -> FIRST_TIME_VERSION
        lastVersionCode > versionCode -> {
            Log.w(TAG, "Current version code ($versionCode) is less then the one recognized on last startup ($lastVersionCode). ")
            Log.w(TAG, "Defensively assuming normal app start.")
            NORMAL
        }

        else -> NORMAL
    }
}

enum class AppStart { FIRST_TIME, FIRST_TIME_VERSION, NORMAL }
