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

import android.content.Context
import android.util.AttributeSet

/** Full-screen overlay that intercepts all touch events, preventing interaction with content behind it. */
open class TouchBlockingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : DimmingView(context, attrs) {
    init {
        isClickable = true
        isFocusable = true
    }
}
