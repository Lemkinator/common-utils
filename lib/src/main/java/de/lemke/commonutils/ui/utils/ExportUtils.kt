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
import android.content.Intent.ACTION_CREATE_DOCUMENT
import android.content.Intent.CATEGORY_OPENABLE
import android.content.Intent.EXTRA_TITLE
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import de.lemke.commonutils.R
import de.lemke.commonutils.data.SaveLocation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ExportUtils"
private const val MIME_TYPE_PNG = "image/png"
private const val EXTENSION_PNG = ".png"

/** The outcome of [saveBitmapToDirectory] and [saveBitmapToUri]; [toast] shows the matching message. */
sealed interface BitmapSaveResult {
    /** The bitmap was written to [location]; [SaveLocation.CUSTOM] stands for a document picked through [exportBitmap]. */
    data class Saved(
        val location: SaveLocation,
    ) : BitmapSaveResult

    /** The bitmap could not be encoded as PNG. */
    data object EncodingFailed : BitmapSaveResult

    /** The target file could not be created or written. */
    data object WriteFailed : BitmapSaveResult

    /** The location needs the document picker on this device; see [SaveLocation.needsPicker]. */
    data object NeedsPicker : BitmapSaveResult
}

/**
 * Launches the document picker through [activityResultLauncher] to create a PNG named after [filename].
 *
 * The launcher's callback writes the bitmap with [saveBitmapToUri].
 * @return true if the picker was launched.
 */
fun Fragment.exportBitmap(
    filename: String,
    activityResultLauncher: ActivityResultLauncher<Intent>,
): Boolean = requireContext().exportBitmap(filename, activityResultLauncher)

/**
 * Launches the document picker through [activityResultLauncher] to create a PNG named after [filename].
 *
 * The launcher's callback writes the bitmap with [saveBitmapToUri].
 * @return true if the picker was launched.
 */
fun Context.exportBitmap(
    filename: String,
    activityResultLauncher: ActivityResultLauncher<Intent>,
): Boolean =
    try {
        activityResultLauncher.launch(
            Intent(ACTION_CREATE_DOCUMENT)
                .addCategory(CATEGORY_OPENABLE)
                .setType(MIME_TYPE_PNG)
                .putExtra(EXTRA_TITLE, filename.toSafeFileName(EXTENSION_PNG)),
        )
        true
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "Error launching document picker", e)
        toast(R.string.commonutils_error_saving_content_is_not_supported_on_device)
        false
    }

/**
 * Writes [bitmap] as a PNG named after [filename] to the public directory of [saveLocation] on [ioDispatcher].
 *
 * Main-safe: run it as the work of a `singleLaunchSuspending` input and pass the result to [toast] in its `then`.
 * A location that [SaveLocation.needsPicker] writes nothing and returns [BitmapSaveResult.NeedsPicker].
 */
suspend fun saveBitmapToDirectory(
    saveLocation: SaveLocation,
    bitmap: Bitmap,
    filename: String,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): BitmapSaveResult {
    val directoryType = saveLocation.publicDirectoryType?.takeUnless { saveLocation.needsPicker } ?: return BitmapSaveResult.NeedsPicker
    return withContext(ioDispatcher) {
        // Scoped storage and the file system throw an open-ended exception set; every failure must toast, not crash.
        @Suppress("TooGenericExceptionCaught")
        try {
            val file = File(Environment.getExternalStoragePublicDirectory(directoryType), filename.toSafeFileName(EXTENSION_PNG))
            if (file.writePngOrDelete(bitmap)) BitmapSaveResult.Saved(saveLocation) else BitmapSaveResult.EncodingFailed
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap to directory", e)
            BitmapSaveResult.WriteFailed
        }
    }
}

/** Shows the message for [result]. */
fun Fragment.toast(result: BitmapSaveResult) = requireContext().toast(result)

/** Shows the message for [result]. */
fun Context.toast(result: BitmapSaveResult) {
    when (result) {
        BitmapSaveResult.Saved(SaveLocation.CUSTOM) -> toast(R.string.commonutils_image_saved)
        is BitmapSaveResult.Saved -> toast(getString(R.string.commonutils_image_saved) + ": ${result.location.toLocalizedString(this)}")
        BitmapSaveResult.EncodingFailed -> toast(R.string.commonutils_error_saving_image)
        BitmapSaveResult.WriteFailed -> toast(R.string.commonutils_error_creating_file)
        BitmapSaveResult.NeedsPicker -> toast(R.string.commonutils_error_saving_content_is_not_supported_on_device)
    }
}

/**
 * Writes [bitmap] as a PNG to [uri], the document picked through [exportBitmap], on [ioDispatcher].
 *
 * Main-safe: run it as the work of a `singleLaunchSuspending` input and pass the result to [toast] in its `then`.
 * A written bitmap returns [BitmapSaveResult.Saved] with [SaveLocation.CUSTOM]; a null [uri] or [bitmap] writes nothing
 * and returns [BitmapSaveResult.WriteFailed].
 */
suspend fun Context.saveBitmapToUri(
    uri: Uri?,
    bitmap: Bitmap?,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): BitmapSaveResult {
    if (uri == null || bitmap == null) return BitmapSaveResult.WriteFailed
    return withContext(ioDispatcher) {
        // Providers and system services throw an open-ended exception set; every failure must toast, not crash.
        @Suppress("TooGenericExceptionCaught")
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                if (bitmap.writePng(outputStream)) BitmapSaveResult.Saved(SaveLocation.CUSTOM) else BitmapSaveResult.EncodingFailed
            } ?: BitmapSaveResult.WriteFailed
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap to uri", e)
            BitmapSaveResult.WriteFailed
        }
    }
}

/** Converts this string to a filesystem-safe filename, appending a timestamp and [extension]. */
fun String.toSafeFileName(extension: String): String =
    "${this}_${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(Date())}"
        .replace("https://", "")
        .replace("[^a-zA-Z0-9]+".toRegex(), "_")
        .replace("_+".toRegex(), "_")
        .replace("^_".toRegex(), "") +
        extension
