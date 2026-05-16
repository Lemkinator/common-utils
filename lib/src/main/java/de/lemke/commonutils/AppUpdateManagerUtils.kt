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
    AppUpdateManagerFactory
        .create(this)
        .appUpdateInfo
        .addOnSuccessListener { appUpdateInfo ->
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
