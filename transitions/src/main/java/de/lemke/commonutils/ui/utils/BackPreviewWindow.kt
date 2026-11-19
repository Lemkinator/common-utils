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
package de.lemke.commonutils.ui.utils

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.reflect.SeslBaseReflector

/** The kind of window a screen runs in, which decides whether the screen draws its own back preview. */
internal enum class BackPreviewWindow {
    /** A window that fills the screen. */
    FullScreen,

    /** A One UI pop-over window, whose frame the system draws outside the content view. */
    PopOver,
}

/**
 * Returns [BackPreviewWindow.PopOver] when `Configuration.semIsPopOver()` returns `true`, else
 * [BackPreviewWindow.FullScreen]. One UI declares that method outside the SDK, and sesl8 has no
 * `SeslConfigurationCompat` wrapper for it.
 */
@SuppressLint("RestrictedApi")
internal fun Configuration.backPreviewWindow(): BackPreviewWindow {
    val semIsPopOver = SeslBaseReflector.getDeclaredMethod(Configuration::class.java, "semIsPopOver")
    return if (SeslBaseReflector.invoke(this, semIsPopOver) == true) BackPreviewWindow.PopOver else BackPreviewWindow.FullScreen
}
