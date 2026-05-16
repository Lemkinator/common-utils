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

import android.app.Activity
import android.text.SpannableString
import androidx.preference.PreferenceFragmentCompat
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity

private const val TAG = "ActivityUtils"

fun setupCommonUtilsOOBEActivity(
    setAcceptedTosVersion: Boolean? = null,
    nextActivity: Class<*>,
) {
    setAcceptedTosVersion?.let { CommonUtilsOOBEActivity.setAcceptedTosVersion = it }
    CommonUtilsOOBEActivity.nextActivity = nextActivity
}

fun setupCommonUtilsOOBEActivity(
    setAcceptedTosVersion: Boolean? = null,
    onContinue: (() -> Unit),
) {
    setAcceptedTosVersion?.let { CommonUtilsOOBEActivity.setAcceptedTosVersion = it }
    CommonUtilsOOBEActivity.onContinue = onContinue
}

fun setupCommonUtilsSettingsActivity(
    vararg preferences: Int,
    initPreferences: suspend PreferenceFragmentCompat.() -> Unit = {},
) {
    CommonUtilsSettingsActivity.preferences = preferences.toList()
    CommonUtilsSettingsActivity.initPreferences = initPreferences
}

fun setupCommonUtilsSettingsActivity(
    preferences: List<Int>,
    initPreferences: suspend PreferenceFragmentCompat.() -> Unit = {},
) {
    CommonUtilsSettingsActivity.preferences = preferences
    CommonUtilsSettingsActivity.initPreferences = initPreferences
}

fun setupCommonUtilsAboutMeActivity(onShareApp: (activity: Activity) -> Unit = {}) {
    CommonUtilsAboutMeActivity.apply {
        this.onShareApp = onShareApp
    }
}

fun setupCommonUtilsAboutActivity(
    appVersion: String,
    optionalText: SpannableString? = null,
) {
    CommonUtilsAboutActivity.apply {
        this.appVersion = appVersion
        this.optionalText = optionalText
    }
}

fun setupCommonUtilsAboutActivity(getAppVersion: suspend () -> String) {
    CommonUtilsAboutActivity.getAppVersion = getAppVersion
}
