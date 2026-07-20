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
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-backed (not a mocked [Context]): [CheckAppStartUseCase] calls
 * `android.util.Log.d(...)` on every path, which throws under a plain, unshadowed JVM `Log` stub
 * — a real Robolectric context is the simplest way to exercise it end-to-end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CheckAppStartUseCaseTest {
    private lateinit var settings: SettingsRepository
    private lateinit var useCase: CheckAppStartUseCase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        settings = SettingsRepository(freshTestPreferences())
        useCase = CheckAppStartUseCase(ctx, settings)
    }

    @Test
    fun `returns FIRST_TIME when no previous install`() {
        settings.lastVersionCode = -1
        useCase(10, "1.0").result shouldBe AppStartResult.FIRST_TIME
    }

    @Test
    fun `returns FIRST_TIME_VERSION on upgrade`() {
        settings.lastVersionCode = 5
        useCase(10, "1.0").result shouldBe AppStartResult.FIRST_TIME_VERSION
    }

    @Test
    fun `returns NORMAL on same version`() {
        settings.lastVersionCode = 10
        useCase(10, "1.0").result shouldBe AppStartResult.NORMAL
    }

    @Test
    fun `returns NORMAL on downgrade`() {
        settings.lastVersionCode = 15
        useCase(10, "1.0").result shouldBe AppStartResult.NORMAL
    }

    @Test
    fun `populates AppStart fields from settings, resources, and call args`() {
        settings.lastVersionCode = 5
        settings.lastVersionName = "0.9"
        settings.acceptedTosVersion = 2
        val result = useCase(10, "1.0")
        result.versionCode shouldBe 10
        result.versionName shouldBe "1.0"
        result.lastVersionCode shouldBe 5
        result.lastVersionName shouldBe "0.9"
        result.tosVersion shouldBe 0 // commonutils_tos_version resource default
        result.acceptedTosVersion shouldBe 2
    }
}
