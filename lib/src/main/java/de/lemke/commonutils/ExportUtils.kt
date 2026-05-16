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
@file:Suppress("unused")

package de.lemke.commonutils

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_CREATE_DOCUMENT
import android.content.Intent.CATEGORY_OPENABLE
import android.content.Intent.EXTRA_TITLE
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.PNG
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Environment
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ExportUtils"
private const val MIME_TYPE_PNG = "image/png"
private const val EXTENSION_PNG = ".png"

enum class SaveLocation {
    CUSTOM,
    DOWNLOADS,
    PICTURES,
    DCIM,
    ;

    companion object {
        val default = CUSTOM

        fun fromStringOrDefault(string: String?): SaveLocation = entries.firstOrNull { it.toString() == string } ?: default

        val entryValues = entries.map { it.name }.toTypedArray()

        fun getLocalizedEntries(context: Context) = entries.map { it.toLocalizedString(context) }.toTypedArray()
    }

    fun toLocalizedString(context: Context): String =
        when (this) {
            CUSTOM -> context.getString(R.string.commonutils_custom)
            DOWNLOADS -> context.getString(R.string.commonutils_downloads)
            PICTURES -> context.getString(R.string.commonutils_pictures)
            DCIM -> context.getString(R.string.commonutils_dcim)
        }
}

fun Fragment.exportBitmap(
    saveLocation: SaveLocation,
    bitmap: Bitmap,
    filename: String,
    activityResultLauncher: ActivityResultLauncher<Intent>?,
): Boolean = requireContext().exportBitmap(saveLocation, bitmap, filename, activityResultLauncher)

fun Context.exportBitmap(
    saveLocation: SaveLocation,
    bitmap: Bitmap,
    filename: String,
    activityResultLauncher: ActivityResultLauncher<Intent>?,
): Boolean =
    if (saveLocation != SaveLocation.CUSTOM && SDK_INT > Build.VERSION_CODES.Q) {
        try {
            val dir: String =
                when (saveLocation) {
                    SaveLocation.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
                    SaveLocation.PICTURES -> Environment.DIRECTORY_PICTURES
                    SaveLocation.DCIM -> Environment.DIRECTORY_DCIM
                }
            Files
                .newOutputStream(File(Environment.getExternalStoragePublicDirectory(dir), filename.toSafeFileName(EXTENSION_PNG)).toPath())
                .use<OutputStream, Boolean> { bitmap.compress(PNG, 100, it) }
            toast(getString(R.string.commonutils_image_saved) + ": ${saveLocation.toLocalizedString(this)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap to directory", e)
            toast(R.string.commonutils_error_creating_file)
            false
        }
    } else {
        try {
            val intent = Intent(ACTION_CREATE_DOCUMENT)
            intent.addCategory(CATEGORY_OPENABLE)
            intent.type = MIME_TYPE_PNG
            intent.putExtra(EXTRA_TITLE, filename.toSafeFileName(EXTENSION_PNG))
            activityResultLauncher?.launch(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching document picker", e)
            toast(R.string.commonutils_error_saving_content_is_not_supported_on_device)
            false
        }
    }

fun Context.saveBitmapToUri(
    uri: Uri?,
    bitmap: Bitmap?,
): Boolean {
    if (uri == null || bitmap == null) {
        toast(R.string.commonutils_error_creating_file)
        return false
    }
    return try {
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            if (bitmap.compress(PNG, 100, outputStream)) {
                toast(R.string.commonutils_image_saved)
                true
            } else {
                toast(R.string.commonutils_error_saving_image)
                false
            }
        } ?: run {
            toast(R.string.commonutils_error_creating_file)
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error saving bitmap to uri", e)
        toast(R.string.commonutils_error_creating_file)
        false
    }
}

fun String.toSafeFileName(extension: String): String =
    "${this}_${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(Date())}"
        .replace("https://", "")
        .replace("[^a-zA-Z0-9]+".toRegex(), "_")
        .replace("_+".toRegex(), "_")
        .replace("^_".toRegex(), "") +
        extension
