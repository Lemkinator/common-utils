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
package de.lemke.commonutils.domain

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class GetInstalledAppsUseCaseTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION")
    @BeforeEach
    fun registerFakeLauncherApp() {
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

    @Test
    fun `invoke delegates to getInstalledAppsForPicker`() {
        GetInstalledAppsUseCase(context)().shouldNotBeEmpty()
    }
}
