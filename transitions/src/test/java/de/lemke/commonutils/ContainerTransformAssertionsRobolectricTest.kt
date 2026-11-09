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

import android.content.Intent
import com.google.android.material.transition.platform.MaterialContainerTransform.FADE_MODE_CROSS
import com.google.android.material.transition.platform.MaterialContainerTransform.FADE_MODE_THROUGH
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContainerTransformAssertionsRobolectricTest {
    private fun transformIntent(
        name: String = "shared",
        duration: Long = 400L,
        fadeMode: Int = FADE_MODE_CROSS,
    ) = Intent()
        .putExtra("commonUtilsTransitionNameKey", name)
        .putExtra("commonUtilsDurationKey", duration)
        .putExtra("commonUtilsFadeModeKey", fadeMode)

    @Test
    fun `matching transform with default duration and fade mode passes`() {
        transformIntent().shouldBeContainerTransformTo("shared")
    }

    @Test
    fun `matching transform with explicit duration and fade mode passes`() {
        transformIntent(duration = 123L, fadeMode = FADE_MODE_THROUGH).shouldBeContainerTransformTo("shared", 123L, FADE_MODE_THROUGH)
    }

    @Test
    fun `different transition name fails and names both`() {
        val error = shouldThrow<AssertionError> { transformIntent("other").shouldBeContainerTransformTo("shared") }

        error.message shouldContain "<shared>"
        error.message shouldContain "<other>"
    }

    @Test
    fun `different duration fails`() {
        shouldThrow<AssertionError> { transformIntent(duration = 123L).shouldBeContainerTransformTo("shared") }
    }

    @Test
    fun `different fade mode fails`() {
        shouldThrow<AssertionError> { transformIntent(fadeMode = FADE_MODE_THROUGH).shouldBeContainerTransformTo("shared") }
    }

    @Test
    fun `intent without a transform fails the transform assertion`() {
        shouldThrow<AssertionError> { Intent().shouldBeContainerTransformTo("shared") }
    }

    @Test
    fun `missing duration fails even when the expected duration equals the former sentinel`() {
        val intent =
            Intent()
                .putExtra("commonUtilsTransitionNameKey", "shared")
                .putExtra("commonUtilsFadeModeKey", FADE_MODE_CROSS)

        val error = shouldThrow<AssertionError> { intent.shouldBeContainerTransformTo("shared", duration = -1L) }

        error.message shouldContain "lasting null ms"
    }

    @Test
    fun `missing fade mode fails even when the expected fade mode equals the former sentinel`() {
        val intent =
            Intent()
                .putExtra("commonUtilsTransitionNameKey", "shared")
                .putExtra("commonUtilsDurationKey", 400L)

        val error = shouldThrow<AssertionError> { intent.shouldBeContainerTransformTo("shared", fadeMode = -1) }

        error.message shouldContain "fade mode null"
    }

    @Test
    fun `intent without a transform passes the no transform assertion`() {
        Intent().putExtra("unrelated", 1).shouldHaveNoContainerTransform()
    }

    @Test
    fun `intent with only a transition name fails the no transform assertion and names the key`() {
        val error =
            shouldThrow<AssertionError> {
                Intent().putExtra("commonUtilsTransitionNameKey", "shared").shouldHaveNoContainerTransform()
            }

        error.message shouldContain "commonUtilsTransitionNameKey"
    }

    @Test
    fun `intent with only a duration fails the no transform assertion`() {
        shouldThrow<AssertionError> { Intent().putExtra("commonUtilsDurationKey", 400L).shouldHaveNoContainerTransform() }
    }

    @Test
    fun `intent with only a fade mode fails the no transform assertion`() {
        shouldThrow<AssertionError> { Intent().putExtra("commonUtilsFadeModeKey", FADE_MODE_CROSS).shouldHaveNoContainerTransform() }
    }
}
