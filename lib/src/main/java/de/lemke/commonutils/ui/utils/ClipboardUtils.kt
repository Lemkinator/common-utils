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
import android.graphics.Bitmap.CompressFormat.PNG
import androidx.fragment.app.Fragment
import de.lemke.commonutils.R
import java.io.File

private const val COMPRESS_QUALITY_MAX = 100

/** Copies [text] to the clipboard under [label] and shows a confirmation toast. */
fun Fragment.copyToClipboard(
    text: String,
    label: String,
): Boolean = requireContext().copyToClipboard(text, label)

/** Copies [text] to the clipboard under [label] and shows a confirmation toast. */
@Suppress("SameReturnValue")
fun Context.copyToClipboard(
    text: String,
    label: String,
): Boolean {
    setClip(ClipData.newPlainText(label, text))
    toast(R.string.commonutils_copied_to_clipboard)
    return true
}

/** Copies [bitmap] to the clipboard via a cached file URI under [label]. */
fun Context.copyToClipboard(
    bitmap: Bitmap,
    label: String,
    shareFileName: String,
): Boolean {
    val cacheFile = File(cacheDir, shareFileName)
    if (!cacheFile.outputStream().use { bitmap.compress(PNG, COMPRESS_QUALITY_MAX, it) }) {
        cacheFile.delete()
        toast(R.string.commonutils_error_share_content_not_supported_on_device)
        return false
    }
    setClip(ClipData.newUri(contentResolver, label, cacheFile.getFileUri(this)))
    toast(R.string.commonutils_copied_to_clipboard)
    return true
}

/** Copies this bitmap to the clipboard via a cached file URI under [label]. */
fun Bitmap.copyToClipboard(
    context: Context,
    label: String,
    shareFileName: String,
): Boolean = context.copyToClipboard(this, label, shareFileName)
