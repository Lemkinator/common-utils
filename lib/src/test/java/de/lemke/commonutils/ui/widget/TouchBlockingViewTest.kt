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

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class TouchBlockingViewTest {
    @Test
    fun `clickable by default`() {
        TouchBlockingView(ApplicationProvider.getApplicationContext()).isClickable shouldBe true
    }

    @Test
    fun `focusable by default`() {
        TouchBlockingView(ApplicationProvider.getApplicationContext()).isFocusable shouldBe true
    }

    @Test
    fun `inherits MATCH_PARENT layout params from DimmingView`() {
        val view = TouchBlockingView(ApplicationProvider.getApplicationContext())
        view.layoutParams.width shouldBe MATCH_PARENT
        view.layoutParams.height shouldBe MATCH_PARENT
    }

    @Test
    fun `is a DimmingView`() {
        TouchBlockingView(ApplicationProvider.getApplicationContext()).shouldBeInstanceOf<DimmingView>()
    }
}
