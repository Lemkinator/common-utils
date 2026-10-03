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
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_STREAM
import android.content.Intent.EXTRA_TEXT
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.fragment.app.Fragment
import de.lemke.commonutils.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SAMSUNG_QUICK_SHARE_PACKAGE = "com.samsung.android.app.sharelive"
private const val MIME_TYPE_PNG = "image/png"
private const val TAG = "SharingUtils"

/**
 * Writes [bitmap] as a PNG cache file named [fileName] on [ioDispatcher] and returns its content URI, or null if
 * writing fails.
 *
 * Main-safe: run it as the work of a `singleLaunchSuspending` input and pass the URI to [shareBitmap] or
 * [quickShareBitmap] in its `then`.
 */
suspend fun Fragment.createBitmapShareUri(
    bitmap: Bitmap,
    fileName: String,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): Uri? = requireContext().createBitmapShareUri(bitmap, fileName, ioDispatcher)

/**
 * Writes [bitmap] as a PNG cache file named [fileName] on [ioDispatcher] and returns its content URI, or null if
 * writing fails.
 *
 * Main-safe: run it as the work of a `singleLaunchSuspending` input and pass the URI to [shareBitmap] or
 * [quickShareBitmap] in its `then`.
 */
suspend fun Context.createBitmapShareUri(
    bitmap: Bitmap,
    fileName: String,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): Uri? =
    withContext(ioDispatcher) {
        // Providers and the file system throw an open-ended exception set; a failure must yield no URI, not crash.
        @Suppress("TooGenericExceptionCaught")
        try {
            writePngCacheUri(bitmap, CacheFileKind.SHARE, fileName) { it }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing bitmap share file", e)
            null
        }
    }

/**
 * Shares the PNG at [uri], from [createBitmapShareUri], via the system share sheet, optionally including [shareText];
 * a null [uri] shows the error toast instead.
 * @return true if the share sheet was started.
 */
fun Fragment.shareBitmap(
    uri: Uri?,
    shareText: String? = null,
): Boolean = requireContext().shareBitmap(uri, shareText)

/**
 * Shares the PNG at [uri], from [createBitmapShareUri], via the system share sheet, optionally including [shareText];
 * a null [uri] shows the error toast instead.
 * @return true if the share sheet was started.
 */
fun Context.shareBitmap(
    uri: Uri?,
    shareText: String? = null,
): Boolean =
    sharePng(uri) {
        val intent =
            Intent(ACTION_SEND).apply {
                clipData = ClipData.newRawUri(null, it)
                putExtra(EXTRA_STREAM, it)
                shareText?.let { text -> putExtra(EXTRA_TEXT, text) }
                type = MIME_TYPE_PNG
                addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            }
        safeStartActivity(Intent.createChooser(intent, null))
    }

/**
 * Shares the PNG at [uri], from [createBitmapShareUri], directly via Samsung Quick Share if available, falling back to
 * the system share sheet; a null [uri] shows the error toast instead.
 * @return true if a share target was started.
 */
fun Fragment.quickShareBitmap(uri: Uri?): Boolean = requireContext().quickShareBitmap(uri)

/**
 * Shares the PNG at [uri], from [createBitmapShareUri], directly via Samsung Quick Share if available, falling back to
 * the system share sheet; a null [uri] shows the error toast instead.
 * @return true if a share target was started.
 */
fun Context.quickShareBitmap(uri: Uri?): Boolean =
    sharePng(uri) {
        createBaseIntent()
            .apply {
                type = MIME_TYPE_PNG
                putExtra(EXTRA_STREAM, it)
            }.start(this)
    }

private fun Context.sharePng(
    uri: Uri?,
    start: (Uri) -> Boolean,
): Boolean {
    if (uri == null) {
        toast(R.string.commonutils_error_share_content_not_supported_on_device)
        return false
    }
    // Providers and system services throw an open-ended exception set; every failure must toast, not crash.
    @Suppress("TooGenericExceptionCaught")
    return try {
        start(uri)
    } catch (e: Exception) {
        Log.e(TAG, "Error sharing bitmap", e)
        toast(R.string.commonutils_error_share_content_not_supported_on_device)
        false
    }
}

internal fun Context.createBaseIntent() =
    Intent().apply {
        addFlags(FLAG_GRANT_READ_URI_PERMISSION)
        action = ACTION_SEND
        if (isSamsungQuickShareAvailable()) {
            `package` = SAMSUNG_QUICK_SHARE_PACKAGE
        }
    }

internal fun Intent.start(context: Context): Boolean {
    try {
        return context.launchGated { context.startActivity(this) }
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "Failed to start activity with specific package: ${e.message}")
        `package` = null
        return context.safeStartActivity(this)
    }
}

/** Returns `true` if the Samsung Quick Share app is installed on this device. */
fun Context.isSamsungQuickShareAvailable(): Boolean =
    try {
        packageManager.getPackageInfo(SAMSUNG_QUICK_SHARE_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }.also {
        Log.i(TAG, "isSamsungQuickShareAvailable: $it")
    }
