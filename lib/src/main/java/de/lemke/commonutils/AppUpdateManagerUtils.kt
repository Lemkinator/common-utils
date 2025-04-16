package de.lemke.commonutils

import android.content.Context
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
import com.google.android.play.core.install.model.UpdateAvailability.UNKNOWN
import com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE
import com.google.android.play.core.install.model.UpdateAvailability.UPDATE_NOT_AVAILABLE

private const val TAG = "AppUpdateManagerUtils"

fun Context.onAppUpdateAvailable(action: () -> Unit) {
    AppUpdateManagerFactory.create(this).appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
        when (appUpdateInfo.updateAvailability()) {
            UPDATE_AVAILABLE -> action()
            DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> Log.d(TAG, "Update in progress")
            UPDATE_NOT_AVAILABLE -> Log.d(TAG, "No update available")
            UNKNOWN -> Log.d(TAG, "Update availability unknown")
        }
    }.addOnFailureListener {
        Log.e(TAG, "Failed to check for app update availability", it)
    }
}

