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

import android.content.res.Configuration
import androidx.reflect.SeslBaseReflector
import de.lemke.commonutils.ui.utils.BackPreviewWindow
import de.lemke.commonutils.ui.utils.backPreviewWindow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackPreviewWindowRobolectricTest {
    private val method = Configuration::class.java.getMethod("toString")

    private fun windowWhenReflectorReturns(value: Any?): BackPreviewWindow {
        mockkStatic(SeslBaseReflector::class)
        every { SeslBaseReflector.invoke(any(), any()) } returns value
        return Configuration().backPreviewWindow()
    }

    @After
    fun tearDown() {
        unmockkStatic(SeslBaseReflector::class)
    }

    @Test
    fun `a reflector result of true is a pop-over`() {
        windowWhenReflectorReturns(true) shouldBe BackPreviewWindow.PopOver
    }

    @Test
    fun `a reflector result of false is a full-screen window`() {
        windowWhenReflectorReturns(false) shouldBe BackPreviewWindow.FullScreen
    }

    @Test
    fun `a null reflector result is a full-screen window`() {
        windowWhenReflectorReturns(null) shouldBe BackPreviewWindow.FullScreen
    }

    @Test
    fun `a reflector result that is no Boolean is a full-screen window`() {
        windowWhenReflectorReturns("true") shouldBe BackPreviewWindow.FullScreen
    }

    @Test
    fun `the lookup asks the Configuration class for semIsPopOver`() {
        mockkStatic(SeslBaseReflector::class)
        every { SeslBaseReflector.getDeclaredMethod(Configuration::class.java, "semIsPopOver") } returns method
        every { SeslBaseReflector.invoke(any(), method) } returns true
        Configuration().backPreviewWindow() shouldBe BackPreviewWindow.PopOver
    }

    @Test
    fun `a system without semIsPopOver is a full-screen window`() {
        Configuration().backPreviewWindow() shouldBe BackPreviewWindow.FullScreen
    }
}
