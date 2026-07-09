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

import android.app.Activity
import android.text.SpannableString
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity

/** Configures the settings activity with the given preference XML resources. */
fun setupCommonUtilsSettingsActivity(vararg preferences: Int) {
    CommonUtilsSettingsActivity.preferences = preferences.toList()
}

/** Configures the settings activity with the given preference XML resource list. */
fun setupCommonUtilsSettingsActivity(preferences: List<Int>) {
    CommonUtilsSettingsActivity.preferences = preferences
}

/** Configures the About Me activity with an optional share-app callback. */
fun setupCommonUtilsAboutMeActivity(onShareApp: (activity: Activity) -> Unit = {}) {
    CommonUtilsAboutMeActivity.apply {
        this.onShareApp = onShareApp
    }
}

/** Configures the About activity with a static version string and optional extra text. */
fun setupCommonUtilsAboutActivity(
    appVersion: String,
    optionalText: SpannableString? = null,
) {
    CommonUtilsAboutActivity.apply {
        this.appVersion = appVersion
        this.optionalText = optionalText
    }
}

/** Configures the About activity with a suspend function that resolves the version string at display time and optional extra text. */
fun setupCommonUtilsAboutActivity(
    getAppVersion: suspend () -> String,
    optionalText: SpannableString? = null,
) {
    CommonUtilsAboutActivity.apply {
        this.getAppVersion = getAppVersion
        this.optionalText = optionalText
    }
}
