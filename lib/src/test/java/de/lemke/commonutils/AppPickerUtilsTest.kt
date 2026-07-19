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
import androidx.picker.di.AppPickerContext
import androidx.picker.model.AppInfo
import androidx.picker.model.AppInfoDataImpl
import androidx.picker.model.viewdata.AppInfoViewData
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.widget.AppPickerStrategy
import de.lemke.commonutils.ui.widget.getInstalledAppsForPicker
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppPickerUtilsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun registerFakeLauncherApp() = registerFakeLauncherApp(context)

    @Test
    fun `convert sets searchable to label and packageName`() {
        val strategy = AppPickerStrategy(AppPickerContext(context))
        val appInfo = AppInfo(packageName = "de.lemke.commonutils", activityName = "")
        val data = AppInfoDataImpl(appInfo, label = "CommonUtils")
        val results = strategy.convert(listOf(data), null).filterIsInstance<AppInfoViewData>()
        results.first().searchable shouldBe listOf("CommonUtils", "de.lemke.commonutils")
    }

    @Test
    fun `convert always includes packageName in searchable`() {
        val strategy = AppPickerStrategy(AppPickerContext(context))
        val appInfo = AppInfo(packageName = "de.lemke.commonutils", activityName = "")
        val data = AppInfoDataImpl(appInfo, label = null)
        val results = strategy.convert(listOf(data), null).filterIsInstance<AppInfoViewData>()
        results.first().searchable shouldContain "de.lemke.commonutils"
    }

    @Test
    fun `getInstalledAppsForPicker sets subLabel to packageName for every entry`() {
        val apps = context.getInstalledAppsForPicker()
        apps.shouldNotBeEmpty()
        val fakeApp = apps.first { it.packageName == "de.lemke.commonutils.fakeapp" }
        fakeApp.subLabel shouldBe "de.lemke.commonutils.fakeapp"
    }
}
