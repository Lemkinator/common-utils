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
package de.lemke.commonutils.data

import android.content.Context
import de.lemke.commonutils.R

/** Target directory for exported images. */
enum class SaveLocation {
    /** User selects the destination via the system document picker. */
    CUSTOM,

    /** Saves to [android.os.Environment.DIRECTORY_DOWNLOADS]. */
    DOWNLOADS,

    /** Saves to [android.os.Environment.DIRECTORY_PICTURES]. */
    PICTURES,

    /** Saves to [android.os.Environment.DIRECTORY_DCIM]. */
    DCIM,
    ;

    /** Returns a user-facing label for this save location. */
    fun toLocalizedString(context: Context): String =
        when (this) {
            CUSTOM -> context.getString(R.string.commonutils_custom)
            DOWNLOADS -> context.getString(R.string.commonutils_downloads)
            PICTURES -> context.getString(R.string.commonutils_pictures)
            DCIM -> context.getString(R.string.commonutils_dcim)
        }

    companion object {
        /** The default save location used when no preference has been set. */
        val default = CUSTOM

        /** Returns the [SaveLocation] whose name matches [string], or [default] if no match is found. */
        fun fromStringOrDefault(string: String?): SaveLocation = entries.firstOrNull { it.toString() == string } ?: default

        /** Array of [SaveLocation] name strings, suitable for use as `DropDownPreference` entry values. */
        val entryValues = entries.map { it.name }.toTypedArray()

        /** Returns an array of localized display strings for each [SaveLocation]. */
        fun getLocalizedEntries(context: Context) = entries.map { it.toLocalizedString(context) }.toTypedArray()
    }
}
