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
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.bindColorSwatch
import de.lemke.commonutils.ui.utils.contrastingTextColor
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ColorUtilsRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `contrastingTextColor just below the luminance threshold returns WHITE`() {
        // rgb(117,117,117): relative luminance 0.177888..., just under sqrt(1.05*0.05)-0.05
        contrastingTextColor(Color.rgb(117, 117, 117)) shouldBe Color.WHITE
    }

    @Test
    fun `contrastingTextColor just above the luminance threshold returns BLACK`() {
        // rgb(118,118,118): relative luminance 0.181164..., just over sqrt(1.05*0.05)-0.05
        contrastingTextColor(Color.rgb(118, 118, 118)) shouldBe Color.BLACK
    }

    @Test
    fun `contrastingTextColor for 808080 returns BLACK`() {
        contrastingTextColor(0xFF808080.toInt()) shouldBe Color.BLACK
    }

    @Test
    fun `contrastingTextColor for black returns WHITE`() {
        contrastingTextColor(Color.BLACK) shouldBe Color.WHITE
    }

    @Test
    fun `contrastingTextColor for white returns BLACK`() {
        contrastingTextColor(Color.WHITE) shouldBe Color.BLACK
    }

    @Test
    fun `contrastingTextColor composites 50 percent alpha black over white backdrop before measuring`() {
        contrastingTextColor(Color.argb(128, 0, 0, 0)) shouldBe Color.BLACK
    }

    @Test
    fun `contrastingTextColor composites 50 percent alpha black over black backdrop before measuring`() {
        contrastingTextColor(Color.argb(128, 0, 0, 0), over = Color.BLACK) shouldBe Color.WHITE
    }

    @Test
    fun `contrastingTextColor for fully transparent background returns BLACK`() {
        // alpha 0 -> composited color is just the default white backdrop -> BLACK
        contrastingTextColor(Color.TRANSPARENT) shouldBe Color.BLACK
    }

    @Test
    fun `bindColorSwatch enabled tints background and sets contrasting text color`() {
        val button = Button(ctx)
        button.bindColorSwatch(Color.RED)
        button.isEnabled.shouldBeTrue()
        button.backgroundTintList?.defaultColor shouldBe Color.RED
        button.currentTextColor shouldBe Color.BLACK
    }

    @Test
    fun `bindColorSwatch with translucent color under a light theme measures contrast against the light window background`() {
        val button = Button(ContextThemeWrapper(ctx, android.R.style.Theme_Light))
        button.bindColorSwatch(Color.argb(128, 0, 0, 0))
        button.currentTextColor shouldBe Color.BLACK
    }

    @Test
    fun `bindColorSwatch with translucent color under a dark theme measures contrast against the dark window background`() {
        val button = Button(ContextThemeWrapper(ctx, android.R.style.Theme))
        button.bindColorSwatch(Color.argb(128, 0, 0, 0))
        button.currentTextColor shouldBe Color.WHITE
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `bindColorSwatch disabled uses the OneUI disabled button-shape presentation`() {
        val button = Button(ctx)
        button.bindColorSwatch(Color.RED, enabled = false)
        button.isEnabled.shouldBeFalse()
        // sesl_show_button_shapes_color_disabled
        button.backgroundTintList?.defaultColor shouldBe 0x66FFFFFF.toInt()
        // commonutils_secondary_text_icon_color (light theme value; values-night overrides it)
        button.currentTextColor shouldBe 0xFF8C8C8C.toInt()
    }
}
