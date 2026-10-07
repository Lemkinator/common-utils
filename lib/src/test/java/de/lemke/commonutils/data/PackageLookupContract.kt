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
package de.lemke.commonutils.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.picker.model.AppInfo
import androidx.picker.model.AppInfoData
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.FAKE_LAUNCHER_APP
import de.lemke.commonutils.registerFakeLauncherApp
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Behaviour every [PackageLookup] shares, so [FakePackageLookup] stays true to [DefaultPackageLookup]. */
abstract class PackageLookupContract {
    /** Returns a lookup on which exactly [installedPackage] is installed among the packages these tests query. */
    abstract fun createLookup(installedPackage: String): PackageLookup

    /** Returns a lookup on which [FAKE_LAUNCHER_APP] is an installed launcher app. */
    abstract fun createLookupWithLauncherApp(): PackageLookup

    @Test
    fun `installedApps lists an installed launcher app with its launcher activity`() =
        runTest {
            createLookupWithLauncherApp().installedApps().map { ComponentName(it.packageName, it.activityName) } shouldContain
                FAKE_LAUNCHER_APP
        }

    @Test
    fun `applicationInfo of an installed package carries its package name`() =
        runTest {
            createLookup(INSTALLED_PACKAGE).applicationInfo(INSTALLED_PACKAGE)?.packageName shouldBe INSTALLED_PACKAGE
        }

    @Test
    fun `applicationInfo of a missing package is null`() =
        runTest {
            createLookup(INSTALLED_PACKAGE).applicationInfo(MISSING_PACKAGE) shouldBe null
        }

    private companion object {
        const val INSTALLED_PACKAGE = "com.example.installed"
        const val MISSING_PACKAGE = "com.example.missing"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DefaultPackageLookupContractTest : PackageLookupContract() {
    override fun createLookup(installedPackage: String): PackageLookup {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).installPackage(PackageInfo().also { it.packageName = installedPackage })
        return DefaultPackageLookup(context, UnconfinedTestDispatcher())
    }

    override fun createLookupWithLauncherApp(): PackageLookup {
        val context = ApplicationProvider.getApplicationContext<Context>()
        registerFakeLauncherApp(context)
        return DefaultPackageLookup(context, UnconfinedTestDispatcher())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FakePackageLookupContractTest : PackageLookupContract() {
    override fun createLookup(installedPackage: String): PackageLookup =
        FakePackageLookup(ApplicationInfo().also { it.packageName = installedPackage })

    override fun createLookupWithLauncherApp(): PackageLookup =
        FakePackageLookup().apply {
            installedAppsResult =
                Result.success(listOf(AppInfoData(AppInfo(FAKE_LAUNCHER_APP.packageName, FAKE_LAUNCHER_APP.className))))
        }
}
