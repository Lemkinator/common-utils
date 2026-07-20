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
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-backed so [Config.sdk] can drive [android.os.Build.VERSION.SDK_INT] and exercise
 * both the modern (API 33+) and legacy `getApplicationInfo` overloads; [Context] itself stays mocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GetApplicationInfoUseCaseTest {
    private val context = mockk<Context>(relaxed = true)
    private val packageManager = mockk<PackageManager>(relaxed = true)
    private lateinit var useCase: GetApplicationInfoUseCase

    @Before
    fun setUp() {
        every { context.packageManager } returns packageManager
        useCase = GetApplicationInfoUseCase(context)
    }

    @Test
    fun `returns ApplicationInfo when package exists on API 33+`() {
        val appInfo = ApplicationInfo().also { it.packageName = "com.example.test" }
        every {
            packageManager.getApplicationInfo("com.example.test", any<PackageManager.ApplicationInfoFlags>())
        } returns appInfo
        useCase("com.example.test") shouldBe appInfo
    }

    @Test
    fun `returns null when package is not found on API 33+`() {
        every {
            packageManager.getApplicationInfo(any(), any<PackageManager.ApplicationInfoFlags>())
        } throws PackageManager.NameNotFoundException("not found")
        useCase("com.nonexistent.pkg") shouldBe null
    }

    @Config(sdk = [32])
    @Test
    fun `returns ApplicationInfo when package exists below API 33`() {
        val appInfo = ApplicationInfo().also { it.packageName = "com.example.test" }
        every { packageManager.getApplicationInfo("com.example.test", 0) } returns appInfo
        useCase("com.example.test") shouldBe appInfo
    }

    @Config(sdk = [32])
    @Test
    fun `returns null when package is not found below API 33`() {
        every {
            packageManager.getApplicationInfo(any(), 0)
        } throws PackageManager.NameNotFoundException("not found")
        useCase("com.nonexistent.pkg") shouldBe null
    }
}
