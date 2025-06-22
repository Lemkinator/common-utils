@file:Suppress("unused")

package de.lemke.commonutils

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import de.lemke.commonutils.AppStartResult.FIRST_TIME
import de.lemke.commonutils.AppStartResult.FIRST_TIME_VERSION
import de.lemke.commonutils.AppStartResult.NORMAL
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity

private const val TAG = "CheckAppStartUtils"

fun AppCompatActivity.checkAppStart(
    versionCode: Int,
    versionName: String,
    versionCodeThreshold: Int = -1,
): AppStart {
    val lastVersionCode = commonUtilsSettings.lastVersionCode
    val lastVersionName = commonUtilsSettings.lastVersionName
    commonUtilsSettings.lastVersionCode = versionCode
    commonUtilsSettings.lastVersionName = versionName
    val tosVersion = resources.getInteger(R.integer.tosVersion)
    val acceptedTosVersion = commonUtilsSettings.acceptedTosVersion
    val result = when {
        lastVersionCode == -1 -> FIRST_TIME
        lastVersionCode < versionCode -> FIRST_TIME_VERSION
        lastVersionCode > versionCode -> {
            Log.w(TAG, "Current version code ($versionCode) is less then the one recognized on last startup ($lastVersionCode). ")
            Log.w(TAG, "Defensively assuming normal app start.")
            NORMAL
        }

        else -> NORMAL
    }
    return AppStart(result, versionCode, versionName, lastVersionCode, lastVersionName, tosVersion, acceptedTosVersion).apply {
        Log.d(TAG, this.toString())
    }
}

fun AppCompatActivity.checkAppStartAndHandleOOBE(
    versionCode: Int,
    versionName: String,
    versionCodeThreshold: Int = -1,
): Boolean {
    if (checkAppStart(versionCode, versionName, versionCodeThreshold).shouldShowOOBE) {
        startActivity(Intent(applicationContext, CommonUtilsOOBEActivity::class.java))
        @Suppress("DEPRECATION") if (SDK_INT < 34) overridePendingTransition(fade_in, fade_out)
        finishAfterTransition()
        return true
    }
    return false
}

enum class AppStartResult { FIRST_TIME, FIRST_TIME_VERSION, NORMAL }

class AppStart(
    val result: AppStartResult,
    val versionCode: Int,
    val versionName: String,
    val lastVersionCode: Int,
    val lastVersionName: String,
    val tosVersion: Int,
    val acceptedTosVersion: Int,
) {
    val isFirstTime get() = lastVersionCode == -1
    val isFirstTimeVersion get() = lastVersionCode < versionCode
    val tosAccepted get() = acceptedTosVersion >= tosVersion
    val shouldShowOOBE get() = isFirstTime || !tosAccepted
    fun versionThresholdPassed(threshold: Int) = lastVersionCode <= threshold && versionCode > threshold
    override fun toString(): String = "AppStart(result=$result, versionCode=$versionCode, versionName='$versionName', " +
            "lastVersionCode=$lastVersionCode, lastVersionName='$lastVersionName', " +
            "tosVersion=$tosVersion, acceptedTosVersion=$acceptedTosVersion)"
}
