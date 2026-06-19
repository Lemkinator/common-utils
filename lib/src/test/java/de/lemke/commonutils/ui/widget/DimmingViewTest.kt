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
package de.lemke.commonutils.ui.widget

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class DimmingViewTest {
    @Test
    fun `default constructor sets MATCH_PARENT layout params`() {
        val view = DimmingView(ApplicationProvider.getApplicationContext())
        view.layoutParams.width shouldBe MATCH_PARENT
        view.layoutParams.height shouldBe MATCH_PARENT
    }

    @Test
    fun `default constructor is not clickable`() {
        DimmingView(ApplicationProvider.getApplicationContext()).isClickable shouldBe false
    }

    @Test
    fun `default constructor is not focusable`() {
        DimmingView(ApplicationProvider.getApplicationContext()).isFocusable shouldBe false
    }

    @Test
    fun `default constructor sets semi-transparent black background`() {
        val view = DimmingView(ApplicationProvider.getApplicationContext())
        val bg = (view.background as? ColorDrawable).shouldNotBeNull()
        // Color.argb(0.5f, 0f, 0f, 0f): alpha = round(0.5 * 255 + 0.5) = 128
        Color.alpha(bg.color) shouldBe 128
        Color.red(bg.color) shouldBe 0
        Color.green(bg.color) shouldBe 0
        Color.blue(bg.color) shouldBe 0
    }

    @Test
    fun `attrs constructor covers withStyledAttributes branch`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val attrs = Robolectric.buildAttributeSet().build()
        DimmingView(ctx, attrs).shouldNotBeNull()
    }
}
