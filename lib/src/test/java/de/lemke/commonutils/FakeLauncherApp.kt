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
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import org.robolectric.Shadows.shadowOf

/** Registers a single fake launcher app so `getInstalledAppsForPicker()` returns a non-empty result under Robolectric. */
@Suppress("DEPRECATION")
internal fun registerFakeLauncherApp(context: Context) {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    shadowOf(context.packageManager).setResolveInfosForIntent(
        launcherIntent,
        listOf(
            ResolveInfo().apply {
                nonLocalizedLabel = "FakeApp"
                activityInfo =
                    ActivityInfo().apply {
                        packageName = "de.lemke.commonutils.fakeapp"
                        name = "de.lemke.commonutils.fakeapp.MainActivity"
                        applicationInfo =
                            ApplicationInfo().apply {
                                packageName = "de.lemke.commonutils.fakeapp"
                                nonLocalizedLabel = "FakeApp"
                                flags = ApplicationInfo.FLAG_INSTALLED
                            }
                    }
            },
        ),
    )
}
