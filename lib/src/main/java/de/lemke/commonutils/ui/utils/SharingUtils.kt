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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_TEXT
import android.content.Intent.EXTRA_TITLE
import android.util.Log
import androidx.fragment.app.Fragment
import de.lemke.commonutils.R
import java.io.File

private const val MIME_TYPE_TEXT = "text/plain"
private const val TAG = "SharingUtils"

/** Shares the app's Play Store link via the system share sheet. */
fun Fragment.shareApp(): Boolean = requireContext().shareApp()

/** Shares the app's Play Store link via the system share sheet. */
fun Context.shareApp(): Boolean =
    safeStartActivity(
        Intent.createChooser(
            Intent().apply {
                action = ACTION_SEND
                type = MIME_TYPE_TEXT
                putExtra(EXTRA_TEXT, getString(R.string.commonutils_playstore_link) + packageName)
            },
            null,
        ),
    )

/** Shares [text] via the system share sheet with an optional chooser [title]. */
fun Fragment.shareText(
    text: String,
    title: String? = null,
): Boolean = requireContext().shareText(text, title)

/** Shares [text] via the system share sheet with an optional chooser [title]. */
fun Context.shareText(
    text: String,
    title: String? = null,
): Boolean {
    Intent().apply {
        action = ACTION_SEND
        putExtra(EXTRA_TEXT, text)
        putExtra(EXTRA_TITLE, title)
        type = MIME_TYPE_TEXT
        return safeStartActivity(Intent.createChooser(this, title))
    }
}

internal fun Context.safeStartActivity(intent: Intent): Boolean {
    try {
        startActivity(intent)
        return true
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "Failed to start activity", e)
        toast(R.string.commonutils_error_share_content_not_supported_on_device)
        return false
    }
}

/** Resolves [shareFileName] to a file under [Context.getCacheDir], rejecting names that would escape it (e.g. `..` traversal). */
internal fun Context.resolveShareCacheFile(shareFileName: String): File {
    val cacheRoot = cacheDir.canonicalPath.trimEnd(File.separatorChar)
    val resolved = File(cacheDir, shareFileName).canonicalPath
    require(resolved == cacheRoot || resolved.startsWith(cacheRoot + File.separatorChar)) {
        "shareFileName must resolve inside cacheDir: $shareFileName"
    }
    return File(resolved)
}
