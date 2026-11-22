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
import android.app.Activity
import android.content.res.Configuration
import androidx.reflect.DeviceInfo
import androidx.reflect.SeslBaseReflector

/** The kind of window a screen runs in. It decides how the screen draws its back preview and how it starts the next page. */
internal enum class WindowKind {
    /** A window that fills the screen. */
    FullScreen,

    /** A One UI pop-over window, whose frame the system draws outside the content view. */
    PopOver,
}

/**
 * Returns [WindowKind.PopOver] when `Configuration.semIsPopOver()` returns `true`, else [WindowKind.FullScreen]. One UI
 * declares that method outside the SDK, and sesl8 has no `SeslConfigurationCompat` wrapper for it. Every other device is
 * [WindowKind.FullScreen] without a lookup, so the reflector logs nothing there.
 */
@SuppressLint("RestrictedApi")
internal fun Configuration.windowKind(): WindowKind {
    if (!DeviceInfo.isOneUI()) return WindowKind.FullScreen
    val semIsPopOver = SeslBaseReflector.getDeclaredMethod(Configuration::class.java, "semIsPopOver")
    return if (SeslBaseReflector.invoke(this, semIsPopOver) == true) WindowKind.PopOver else WindowKind.FullScreen
}

/** The [WindowKind] of this activity's own window. */
internal fun Activity.windowKind(): WindowKind = resources.configuration.windowKind()
