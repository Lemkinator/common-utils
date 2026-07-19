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
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.registerFakeLauncherApp
import de.lemke.commonutils.ui.widget.getInstalledAppsForPicker
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GetInstalledAppsUseCaseTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun registerFakeLauncherApp() = registerFakeLauncherApp(context)

    @Test
    fun `invoke delegates to getInstalledAppsForPicker`() =
        runTest {
            val result = GetInstalledAppsUseCase(context, UnconfinedTestDispatcher())()
            result.shouldNotBeEmpty()
            result shouldBe context.getInstalledAppsForPicker()
        }
}
