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
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GetApplicationInfoUseCaseTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun installPackage() {
        shadowOf(context.packageManager).installPackage(PackageInfo().also { it.packageName = INSTALLED_PACKAGE })
    }

    @Test
    fun `returns the ApplicationInfo of an installed package on API 33+`() =
        runTest {
            GetApplicationInfoUseCase(context, UnconfinedTestDispatcher(testScheduler))(INSTALLED_PACKAGE)?.packageName shouldBe
                INSTALLED_PACKAGE
        }

    @Test
    fun `returns null for a missing package on API 33+`() =
        runTest {
            GetApplicationInfoUseCase(context, UnconfinedTestDispatcher(testScheduler))(MISSING_PACKAGE) shouldBe null
        }

    @Config(sdk = [32])
    @Test
    fun `returns the ApplicationInfo of an installed package below API 33`() =
        runTest {
            GetApplicationInfoUseCase(context, UnconfinedTestDispatcher(testScheduler))(INSTALLED_PACKAGE)?.packageName shouldBe
                INSTALLED_PACKAGE
        }

    @Config(sdk = [32])
    @Test
    fun `returns null for a missing package below API 33`() =
        runTest {
            GetApplicationInfoUseCase(context, UnconfinedTestDispatcher(testScheduler))(MISSING_PACKAGE) shouldBe null
        }

    @Test
    fun `looks the package up on the injected IO dispatcher`() =
        runTest {
            val io = HeldDispatcher()
            val lookup = async { GetApplicationInfoUseCase(context, io)(INSTALLED_PACKAGE) }
            runCurrent()

            lookup.isCompleted.shouldBeFalse()
            io.held shouldHaveSize 1

            io.held.removeFirst().run()
            lookup.await()?.packageName shouldBe INSTALLED_PACKAGE
        }

    private class HeldDispatcher : CoroutineDispatcher() {
        val held = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            held += block
        }
    }

    private companion object {
        const val INSTALLED_PACKAGE = "com.example.installed"
        const val MISSING_PACKAGE = "com.example.missing"
    }
}
