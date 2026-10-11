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

import android.content.pm.ApplicationInfo
import de.lemke.commonutils.data.FakePackageLookup
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GetApplicationInfoUseCaseTest {
    private val lookup = FakePackageLookup(ApplicationInfo().also { it.packageName = "com.example.installed" })

    @Test
    fun `returns the ApplicationInfo the package lookup holds for the package`() =
        runTest {
            GetApplicationInfoUseCase(lookup)("com.example.installed")?.packageName shouldBe "com.example.installed"
            lookup.lookedUpPackages shouldBe listOf("com.example.installed")
        }

    @Test
    fun `returns null for a package the package lookup does not hold`() =
        runTest {
            GetApplicationInfoUseCase(lookup)("com.example.missing") shouldBe null
            lookup.lookedUpPackages shouldBe listOf("com.example.missing")
        }
}
