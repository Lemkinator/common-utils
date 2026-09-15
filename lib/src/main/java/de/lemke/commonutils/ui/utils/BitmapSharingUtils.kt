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
import android.graphics.Bitmap.CompressFormat.PNG
import android.util.Log
import androidx.fragment.app.Fragment
import de.lemke.commonutils.R

private const val SAMSUNG_QUICK_SHARE_PACKAGE = "com.samsung.android.app.sharelive"
private const val MIME_TYPE_PNG = "image/png"
private const val TAG = "SharingUtils"
private const val COMPRESS_QUALITY_MAX = 100

/** Shares [bitmap] via the system share sheet, optionally including [shareText]. */
fun Fragment.shareBitmap(
    bitmap: Bitmap,
    shareFileName: String,
    shareText: String? = null,
): Boolean = bitmap.share(requireContext(), shareFileName, shareText)

/** Shares [bitmap] via the system share sheet, optionally including [shareText]. */
fun Context.shareBitmap(
    bitmap: Bitmap,
    shareFileName: String,
    shareText: String? = null,
): Boolean = bitmap.share(this, shareFileName, shareText)

/** Writes this bitmap to a cache file and shares it via the system share sheet, optionally including [shareText]. */
fun Bitmap.share(
    context: Context,
    shareFileName: String,
    shareText: String? = null,
): Boolean =
    @Suppress("TooGenericExceptionCaught")
    try {
        val cacheFile = context.resolveShareCacheFile(shareFileName)
        if (!cacheFile.outputStream().use { compress(PNG, COMPRESS_QUALITY_MAX, it) }) {
            cacheFile.delete()
            context.toast(R.string.commonutils_error_share_content_not_supported_on_device)
            return false
        }
        val uri = cacheFile.getFileUri(context)
        Intent(ACTION_SEND).run {
            clipData = ClipData.newRawUri(shareFileName, uri)
            putExtra(EXTRA_STREAM, uri)
            shareText?.let { putExtra(EXTRA_TEXT, it) }
            type = MIME_TYPE_PNG
            addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            context.safeStartActivity(Intent.createChooser(this, null))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error sharing bitmap", e)
        context.toast(R.string.commonutils_error_share_content_not_supported_on_device)
        false
    }

/** Shares [bitmap] directly via Samsung Quick Share if available, falling back to the system share sheet. */
fun Fragment.quickShareBitmap(
    bitmap: Bitmap,
    shareFileName: String,
): Boolean = bitmap.quickShare(requireContext(), shareFileName)

/** Shares [bitmap] directly via Samsung Quick Share if available, falling back to the system share sheet. */
fun Context.quickShareBitmap(
    bitmap: Bitmap,
    shareFileName: String,
): Boolean = bitmap.quickShare(this, shareFileName)

/** Shares this bitmap directly via Samsung Quick Share if available, falling back to the system share sheet. */
fun Bitmap.quickShare(
    context: Context,
    shareFileName: String,
): Boolean =
    @Suppress("TooGenericExceptionCaught")
    try {
        val cacheFile = context.resolveShareCacheFile(shareFileName)
        if (!cacheFile.outputStream().use { compress(PNG, COMPRESS_QUALITY_MAX, it) }) {
            cacheFile.delete()
            context.toast(R.string.commonutils_error_share_content_not_supported_on_device)
            return false
        }
        context
            .createBaseIntent()
            .apply {
                type = MIME_TYPE_PNG
                putExtra(EXTRA_STREAM, cacheFile.getFileUri(context))
            }.start(context)
    } catch (e: Exception) {
        Log.e(TAG, "Error sharing bitmap via Quick Share", e)
        context.toast(R.string.commonutils_error_share_content_not_supported_on_device)
        false
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
        context.startActivity(this)
        return true
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
