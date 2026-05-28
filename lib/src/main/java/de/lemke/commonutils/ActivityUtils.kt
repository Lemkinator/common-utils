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
import de.lemke.commonutils.ui.fragment.CommonUtilsAboutFragment
import de.lemke.commonutils.ui.fragment.CommonUtilsAboutMeFragment
import de.lemke.commonutils.ui.fragment.CommonUtilsOOBEFragment
import de.lemke.commonutils.ui.fragment.CommonUtilsSettingsFragment

/** Configures the OOBE fragment navigation action and optional fragment-level settings. */
fun setupCommonUtilsNavGraph(
    oobeCompleteNavAction: Int = 0,
    oobeTosVersion: Boolean = true,
    preferences: List<Int> = CommonUtilsSettingsFragment.preferences,
    initPreferences: suspend PreferenceFragmentCompat.() -> Unit = {},
    appVersion: String = "",
    getAppVersion: suspend () -> String = { "" },
    optionalText: SpannableString? = null,
    onShareApp: (activity: Activity) -> Unit = {},
) {
    CommonUtilsOOBEFragment.onCompleteNavAction = oobeCompleteNavAction
    CommonUtilsOOBEFragment.setAcceptedTosVersion = oobeTosVersion
    CommonUtilsSettingsFragment.preferences = preferences
    CommonUtilsSettingsFragment.initPreferences = initPreferences
    CommonUtilsAboutFragment.appVersion = appVersion
    CommonUtilsAboutFragment.getAppVersion = getAppVersion
    CommonUtilsAboutFragment.optionalText = optionalText
    CommonUtilsAboutMeFragment.onShareApp = onShareApp
}
