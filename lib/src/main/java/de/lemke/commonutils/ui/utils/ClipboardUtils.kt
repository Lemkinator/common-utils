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

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.fragment.app.Fragment
import de.lemke.commonutils.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ClipboardUtils"

/** Copies [text] to the clipboard under [label] and shows a confirmation toast. */
fun Fragment.copyToClipboard(
    text: String,
    label: String,
): Boolean = requireContext().copyToClipboard(text, label)

/** Copies [text] to the clipboard under [label] and shows a confirmation toast. */
fun Context.copyToClipboard(
    text: String,
    label: String,
): Boolean = copyToClipboard(ClipData.newPlainText(label, text))

/**
 * Sets [clip] as the primary clip and shows a confirmation toast; a null [clip], from a failed [createBitmapClip],
 * shows the error toast instead.
 * @return true if the clipboard holds [clip].
 */
fun Fragment.copyToClipboard(clip: ClipData?): Boolean = requireContext().copyToClipboard(clip)

/**
 * Sets [clip] as the primary clip and shows a confirmation toast; a null [clip], from a failed [createBitmapClip],
 * shows the error toast instead.
 * @return true if the clipboard holds [clip].
 */
fun Context.copyToClipboard(clip: ClipData?): Boolean {
    if (clip == null) {
        toast(R.string.commonutils_error_share_content_not_supported_on_device)
        return false
    }
    // Providers and system services throw an open-ended exception set; every failure must toast, not crash.
    @Suppress("TooGenericExceptionCaught")
    return try {
        setClip(clip)
        toast(R.string.commonutils_copied_to_clipboard)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Error copying to clipboard", e)
        toast(R.string.commonutils_error_share_content_not_supported_on_device)
        false
    }
}

/**
 * Writes [bitmap] as a PNG cache file named [fileName] on [ioDispatcher] and returns a clip of its content URI under
 * [label], or null if writing fails. Each write gets a file of its own, so no later write with the same [fileName]
 * changes the file of an earlier clip. The first [createBitmapClip] call that starts more than one day after this write
 * and returns a clip deletes the file; a call that returns null deletes no earlier file. That call deletes the file
 * even if its own clip never reaches the clipboard, so a clipboard that still holds the older clip points to a deleted
 * file. The system can delete the file earlier when it evicts cache files. A receiver that keeps the URI longer reads
 * a dead URI.
 *
 * Main-safe: run it as the work of a `singleLaunchSuspending` input and pass the clip to [copyToClipboard] in its
 * `then`.
 */
suspend fun Fragment.createBitmapClip(
    bitmap: Bitmap,
    label: String,
    fileName: String,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): ClipData? = requireContext().createBitmapClip(bitmap, label, fileName, ioDispatcher)

/**
 * Writes [bitmap] as a PNG cache file named [fileName] on [ioDispatcher] and returns a clip of its content URI under
 * [label], or null if writing fails. Each write gets a file of its own, so no later write with the same [fileName]
 * changes the file of an earlier clip. The first [createBitmapClip] call that starts more than one day after this write
 * and returns a clip deletes the file; a call that returns null deletes no earlier file. That call deletes the file
 * even if its own clip never reaches the clipboard, so a clipboard that still holds the older clip points to a deleted
 * file. The system can delete the file earlier when it evicts cache files. A receiver that keeps the URI longer reads
 * a dead URI.
 *
 * Main-safe: run it as the work of a `singleLaunchSuspending` input and pass the clip to [copyToClipboard] in its
 * `then`.
 */
suspend fun Context.createBitmapClip(
    bitmap: Bitmap,
    label: String,
    fileName: String,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): ClipData? =
    withContext(ioDispatcher) {
        // Providers and the file system throw an open-ended exception set; a failure must yield no clip, not crash.
        @Suppress("TooGenericExceptionCaught")
        try {
            writePngCacheUri(bitmap, CacheFileKind.CLIPBOARD, fileName) { ClipData.newUri(contentResolver, label, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing bitmap clip", e)
            null
        }
    }
