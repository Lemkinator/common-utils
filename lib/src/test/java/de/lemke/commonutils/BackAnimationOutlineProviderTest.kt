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

import io.kotest.matchers.floats.shouldBeExactly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class BackAnimationOutlineProviderTest {
    @Test
    fun `initial radius and progress are zero`() {
        val provider = BackAnimationOutlineProvider()
        provider.radius shouldBeExactly 0f
        provider.progress shouldBeExactly 0f
    }

    @Test
    fun `setting progress updates radius to progress times 100`() {
        val provider = BackAnimationOutlineProvider()
        provider.progress = 0.5f
        provider.radius shouldBeExactly 50f
    }

    @Test
    fun `full progress gives radius 100`() {
        val provider = BackAnimationOutlineProvider()
        provider.progress = 1f
        provider.radius shouldBeExactly 100f
    }

    @Test
    fun `zero progress gives radius 0`() {
        val provider = BackAnimationOutlineProvider()
        provider.progress = 0f
        provider.radius shouldBeExactly 0f
    }
}
