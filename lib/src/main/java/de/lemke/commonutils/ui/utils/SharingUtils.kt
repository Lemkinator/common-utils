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
import android.net.Uri
import android.util.Log
import androidx.fragment.app.Fragment
import de.lemke.commonutils.NoCoverage
import de.lemke.commonutils.R
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

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

/** How long a cache write stays on disk before a later write of its kind deletes it. */
internal val CACHE_WRITE_RETENTION: Duration = 1.days

/**
 * A uniquely named directory that holds the file of exactly one cache write, so no other write can overwrite that file.
 * Its name prefix marks it as the library's own, so the cleanup never touches other files in the same directory.
 */
internal class CacheWriteDirectory private constructor(
    val root: File,
) {
    /** True if this directory was last modified more than [CACHE_WRITE_RETENTION] before [nowMillis]. */
    private fun isExpiredAt(nowMillis: Long): Boolean = root.lastModified() < nowMillis - CACHE_WRITE_RETENTION.inWholeMilliseconds

    /** Resolves [fileName] to a file inside this directory, rejecting names that resolve to it or escape it (e.g. `..` traversal). */
    fun resolve(fileName: String): File {
        val resolved = File(root, fileName).canonicalPath
        require(resolved.startsWith(root.canonicalPath + File.separatorChar)) { "fileName escapes the write directory: $fileName" }
        return File(resolved)
    }

    /** Deletes this directory with its file; whatever remains goes with a later write's cleanup. */
    fun delete() {
        root.deleteRecursively()
    }

    companion object {
        private const val NAME_PREFIX = "commonutils-"

        /**
         * Creates a new write directory inside [parent].
         * @throws java.io.IOException if the directory cannot be created.
         */
        fun createIn(parent: File): CacheWriteDirectory =
            CacheWriteDirectory(Files.createTempDirectory(parent.toPath(), NAME_PREFIX).toFile())

        /** Deletes the write directories inside [parent] that are expired at [nowMillis]; foreign entries stay. */
        fun deleteExpiredIn(
            parent: File,
            nowMillis: Long,
        ) {
            parent.listFiles { file -> file.isDirectory && file.name.startsWith(NAME_PREFIX) }.orEmpty().forEach { file ->
                val directory = CacheWriteDirectory(file)
                if (directory.isExpiredAt(nowMillis)) directory.delete()
            }
        }
    }
}

/**
 * Deletes the write directories of [kind] last modified more than [CACHE_WRITE_RETENTION] ago, then creates the
 * directory of a new write of [kind].
 * @throws java.io.IOException if the directory cannot be created.
 */
internal fun Context.createCacheWriteDirectory(kind: CacheFileKind): CacheWriteDirectory {
    val kindDirectory = File(cacheDir, kind.directoryName).apply { mkdirs() }
    CacheWriteDirectory.deleteExpiredIn(kindDirectory, System.currentTimeMillis())
    return CacheWriteDirectory.createIn(kindDirectory)
}

/** Encodes this bitmap as a lossless PNG into [out]; returns false if the bitmap cannot be encoded. */
internal fun Bitmap.writePng(out: OutputStream): Boolean = compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)

// File.outputStream() is inline; its FileOutputStream constructor is inlined at every call site
// and attributed as an uncoverable branch by JaCoCo on Linux/CI. Wrapping it here keeps the
// inline expansion inside excluded code while the call site stays a plain Kotlin function call.
@NoCoverage
private fun File.openOutputStream(): FileOutputStream = outputStream()

/**
 * Writes [bitmap] as a PNG to this file.
 * @return false if the bitmap cannot be encoded; I/O errors propagate.
 */
private fun File.writePng(bitmap: Bitmap): Boolean = openOutputStream().use(bitmap::writePng)

/**
 * Writes [bitmap] as a PNG to this file and deletes the file if the bitmap cannot be encoded or the write throws.
 * @return false if the bitmap cannot be encoded; I/O errors propagate.
 */
internal fun File.writePngOrDelete(bitmap: Bitmap): Boolean =
    runCatching { writePng(bitmap) }
        .also { if (it.getOrNull() != true) deleteOrLog() }
        .getOrThrow()

/** Deletes this file and logs a warning if it remains. */
internal fun File.deleteOrLog() {
    if (!delete() && exists()) Log.w(TAG, "Could not delete $path")
}

/**
 * Writes [bitmap] as a PNG named [fileName] into a new write directory of [kind] and maps the file's content URI with
 * [transform]; deletes that directory if the bitmap cannot be encoded or anything throws.
 * @return the mapped URI, or null if the bitmap cannot be encoded; I/O, path and provider errors propagate.
 */
internal fun <R : Any> Context.writePngCacheUri(
    bitmap: Bitmap,
    kind: CacheFileKind,
    fileName: String,
    transform: (Uri) -> R,
): R? {
    val directory = createCacheWriteDirectory(kind)
    return runCatching { directory.resolve(fileName).takeIf { it.writePng(bitmap) }?.let { transform(it.getFileUri(this)) } }
        .also { if (it.getOrNull() == null) directory.delete() }
        .getOrThrow()
}
