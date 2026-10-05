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

/** Outcome of [createBitmapShareFile], passed to [shareBitmap] or [quickShareBitmap]. */
sealed interface BitmapShareFile {
    /** The PNG cache file was written; [uri] is its content URI. */
    data class Written(
        val uri: Uri,
    ) : BitmapShareFile

    /** The activity's launch latch drops launches, so nothing was written and the share drops silently. */
    data object Dropped : BitmapShareFile

    /** Writing the PNG cache file failed. */
    data object Failed : BitmapShareFile
}

/**
 * Writes [bitmap] as a PNG cache file named [fileName] on [ioDispatcher]. Each write gets a file of its own, so no
 * later write with the same [fileName] changes the file of a pending share. The first [createBitmapShareFile] call that
 * starts more than one day after this write and returns [BitmapShareFile.Written] deletes the file; a call that returns
 * [BitmapShareFile.Dropped] writes nothing, and one that returns [BitmapShareFile.Dropped] or [BitmapShareFile.Failed]
 * deletes no earlier file. The system can delete the file earlier when it evicts cache files. A receiver that keeps
 * the URI longer reads a dead URI.
 *
 * Main-safe. A ViewModel calls the [Context.createBitmapShareFile] overload with an application context instead; a
 * ViewModel that holds this fragment leaks it, and the call throws once the fragment detaches.
 * While the launch latch of this fragment's activity drops launches, it returns [BitmapShareFile.Dropped] without
 * writing, unless it runs as the work of a `singleLaunchSuspending` input.
 */
suspend fun Fragment.createBitmapShareFile(
    bitmap: Bitmap,
    fileName: String,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): BitmapShareFile = requireContext().createBitmapShareFile(bitmap, fileName, ioDispatcher)

/**
 * Writes [bitmap] as a PNG cache file named [fileName] on [ioDispatcher]. Each write gets a file of its own, so no
 * later write with the same [fileName] changes the file of a pending share. The first [createBitmapShareFile] call that
 * starts more than one day after this write and returns [BitmapShareFile.Written] deletes the file; a call that returns
 * [BitmapShareFile.Dropped] writes nothing, and one that returns [BitmapShareFile.Dropped] or [BitmapShareFile.Failed]
 * deletes no earlier file. The system can delete the file earlier when it evicts cache files. A receiver that keeps
 * the URI longer reads a dead URI.
 *
 * Main-safe: call it from `viewModelScope` and expose the result as UI state; a RESUMED collector passes it to
 * [shareBitmap] or [quickShareBitmap]. The collector resets that state after it acts on it, since a `StateFlow` replays
 * its value on each resume. With an activity context, while its launch latch drops launches, it returns
 * [BitmapShareFile.Dropped] without writing, unless it runs as the work of a `singleLaunchSuspending` input.
 */
suspend fun Context.createBitmapShareFile(
    bitmap: Bitmap,
    fileName: String,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): BitmapShareFile {
    if (!admitsLaunchPreparation()) return BitmapShareFile.Dropped
    return withContext(ioDispatcher) {
        // Providers and the file system throw an open-ended exception set; a failure must yield Failed, not crash.
        @Suppress("TooGenericExceptionCaught")
        try {
            writePngCacheUri(bitmap, CacheFileKind.SHARE, fileName, BitmapShareFile::Written) ?: BitmapShareFile.Failed
        } catch (e: Exception) {
            Log.e(TAG, "Error writing bitmap share file", e)
            BitmapShareFile.Failed
        }
    }
}

/**
 * Shares the PNG of [file], from [createBitmapShareFile], via the system share sheet, optionally including [shareText].
 * A [BitmapShareFile.Failed] file shows the error toast; a [BitmapShareFile.Dropped] file starts nothing.
 * @return true if the share sheet was started.
 */
fun Fragment.shareBitmap(
    file: BitmapShareFile,
    shareText: String? = null,
): Boolean = requireContext().shareBitmap(file, shareText)

/**
 * Shares the PNG of [file], from [createBitmapShareFile], via the system share sheet, optionally including [shareText].
 * A [BitmapShareFile.Failed] file shows the error toast; a [BitmapShareFile.Dropped] file starts nothing.
 * @return true if the share sheet was started.
 */
fun Context.shareBitmap(
    file: BitmapShareFile,
    shareText: String? = null,
): Boolean =
    sharePng(file) {
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
 * Shares the PNG of [file], from [createBitmapShareFile], directly via Samsung Quick Share if available, falling back
 * to the system share sheet. A [BitmapShareFile.Failed] file shows the error toast; a [BitmapShareFile.Dropped] file
 * starts nothing.
 * @return true if a share target was started.
 */
fun Fragment.quickShareBitmap(file: BitmapShareFile): Boolean = requireContext().quickShareBitmap(file)

/**
 * Shares the PNG of [file], from [createBitmapShareFile], directly via Samsung Quick Share if available, falling back
 * to the system share sheet. A [BitmapShareFile.Failed] file shows the error toast; a [BitmapShareFile.Dropped] file
 * starts nothing.
 * @return true if a share target was started.
 */
fun Context.quickShareBitmap(file: BitmapShareFile): Boolean =
    sharePng(file) {
        createBaseIntent()
            .apply {
                type = MIME_TYPE_PNG
                putExtra(EXTRA_STREAM, it)
            }.start(this)
    }

private fun Context.sharePng(
    file: BitmapShareFile,
    start: (Uri) -> Boolean,
): Boolean =
    when (file) {
        is BitmapShareFile.Written -> startOrToast { start(file.uri) }
        BitmapShareFile.Dropped -> false
        BitmapShareFile.Failed -> false.also { toast(R.string.commonutils_error_share_content_not_supported_on_device) }
    }

private fun Context.startOrToast(start: () -> Boolean): Boolean {
    // Providers and system services throw an open-ended exception set; every failure must toast, not crash.
    @Suppress("TooGenericExceptionCaught")
    return try {
        start()
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
