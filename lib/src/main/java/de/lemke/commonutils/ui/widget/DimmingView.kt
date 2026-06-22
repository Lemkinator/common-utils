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
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.content.withStyledAttributes
import de.lemke.commonutils.R

/** Full-screen semi-transparent overlay used as a background for tip popups. */
open class DimmingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    init {
        if (attrs == null) {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setBackgroundColor(Color.argb(DEFAULT_ALPHA, 0f, 0f, 0f))
        } else {
            context.withStyledAttributes(attrs, R.styleable.DimmingView) {
                setBackgroundColor(Color.argb(getFloat(R.styleable.DimmingView_alpha, DEFAULT_ALPHA), 0f, 0f, 0f))
            }
        }
    }

    companion object {
        private const val DEFAULT_ALPHA = 0.5f
    }
}
