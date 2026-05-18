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
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TouchBlockingViewTest {
    @Test
    fun `clickable by default`() {
        val view = TouchBlockingView(ApplicationProvider.getApplicationContext())
        assertThat(view.isClickable).isTrue()
    }

    @Test
    fun `focusable by default`() {
        val view = TouchBlockingView(ApplicationProvider.getApplicationContext())
        assertThat(view.isFocusable).isTrue()
    }

    @Test
    fun `inherits MATCH_PARENT layout params from DimmingView`() {
        val view = TouchBlockingView(ApplicationProvider.getApplicationContext())
        assertThat(view.layoutParams.width).isEqualTo(MATCH_PARENT)
        assertThat(view.layoutParams.height).isEqualTo(MATCH_PARENT)
    }

    @Test
    fun `is a DimmingView`() {
        val view = TouchBlockingView(ApplicationProvider.getApplicationContext())
        assertThat(view).isInstanceOf(DimmingView::class.java)
    }
}
