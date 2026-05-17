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
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DimmingViewTest {
    @Test
    fun `default constructor sets MATCH_PARENT layout params`() {
        val view = DimmingView(ApplicationProvider.getApplicationContext())
        assertThat(view.layoutParams.width).isEqualTo(MATCH_PARENT)
        assertThat(view.layoutParams.height).isEqualTo(MATCH_PARENT)
    }

    @Test
    fun `default constructor is not clickable`() {
        val view = DimmingView(ApplicationProvider.getApplicationContext())
        assertThat(view.isClickable).isFalse()
    }

    @Test
    fun `default constructor is not focusable`() {
        val view = DimmingView(ApplicationProvider.getApplicationContext())
        assertThat(view.isFocusable).isFalse()
    }

    @Test
    fun `default constructor sets semi-transparent black background`() {
        val view = DimmingView(ApplicationProvider.getApplicationContext())
        val bg = view.background as? ColorDrawable
        assertThat(bg).isNotNull()
        // Color.argb(0.5f, 0f, 0f, 0f): alpha = round(0.5 * 255 + 0.5) = 128
        assertThat(Color.alpha(bg!!.color)).isEqualTo(128)
        assertThat(Color.red(bg.color)).isEqualTo(0)
        assertThat(Color.green(bg.color)).isEqualTo(0)
        assertThat(Color.blue(bg.color)).isEqualTo(0)
    }
}
