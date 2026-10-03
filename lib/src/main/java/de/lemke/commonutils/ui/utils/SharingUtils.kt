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
import android.graphics.Bitmap
import android.util.Log
import androidx.fragment.app.Fragment
import de.lemke.commonutils.NoCoverage
import de.lemke.commonutils.R
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

private const val MIME_TYPE_TEXT = "text/plain"
private const val TAG = "SharingUtils"
private const val PNG_QUALITY = 100

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
        return launchGated { startActivity(intent) }
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "Failed to start activity", e)
        toast(R.string.commonutils_error_share_content_not_supported_on_device)
        return false
    }
}

/** Kinds of cache file handed to other apps, each in its own [Context.getCacheDir] subdirectory so no kind overwrites another's. */
internal enum class CacheFileKind(
    val directoryName: String,
) {
    SHARE("share"),
    CLIPBOARD("clipboard"),
}

/** Resolves [fileName] to a file in [kind]'s cache directory, rejecting names that would escape it (e.g. `..` traversal). */
internal fun Context.resolveCacheFile(
    kind: CacheFileKind,
    fileName: String,
): File {
    val directory = File(cacheDir, kind.directoryName).apply { mkdirs() }
    val root = directory.canonicalPath
    val resolved = File(directory, fileName).canonicalPath
    require(resolved == root || resolved.startsWith(root + File.separatorChar)) {
        "fileName must resolve inside ${kind.directoryName}: $fileName"
    }
    return File(resolved)
}

/** Encodes this bitmap as a lossless PNG into [out]; returns false if the bitmap cannot be encoded. */
internal fun Bitmap.writePng(out: OutputStream): Boolean = compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)

// File.outputStream() is inline; its FileOutputStream constructor is inlined at every call site
// and attributed as an uncoverable branch by JaCoCo on Linux/CI. Wrapping it here keeps the
// inline expansion inside excluded code while the call site stays a plain Kotlin function call.
@NoCoverage
private fun File.openOutputStream(): FileOutputStream = outputStream()

/**
 * Writes [bitmap] as a PNG to this file and deletes the file if the bitmap cannot be encoded or the write throws.
 * @return false if the bitmap cannot be encoded; I/O errors propagate.
 */
internal fun File.writePngOrDelete(bitmap: Bitmap): Boolean =
    runCatching { openOutputStream().use(bitmap::writePng) }
        .also { if (it.getOrNull() != true) delete() }
        .getOrThrow()

/**
 * Writes [bitmap] as a PNG to [fileName] in [kind]'s cache directory.
 * @return the written file, or null if the bitmap cannot be encoded; I/O and path errors propagate.
 */
internal fun Context.writePngCacheFile(
    bitmap: Bitmap,
    kind: CacheFileKind,
    fileName: String,
): File? = resolveCacheFile(kind, fileName).takeIf { it.writePngOrDelete(bitmap) }
