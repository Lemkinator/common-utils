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

import android.content.Context
import android.content.Intent.ACTION_SEND_MULTIPLE
import android.content.Intent.EXTRA_STREAM
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

private const val MIME_TYPE_PNG = "image/png"
private const val TAG = "SharingUtils"

/** Shares this image file via the system share sheet. */
fun File.share(context: Context): Boolean = listOf(this).share(context)

/** Shares all image files in this list via the system share sheet (multi-file if more than one). */
fun List<File>.share(context: Context): Boolean {
    val contentUris = map { f -> f.getFileUri(context) }

    if (contentUris.isEmpty()) {
        Log.e(TAG, "No file to share.")
        return false
    }

    context.createBaseIntent().apply {
        type = MIME_TYPE_PNG
        if (contentUris.size == 1) {
            putExtra(EXTRA_STREAM, contentUris[0])
        } else {
            action = ACTION_SEND_MULTIPLE
            putExtra(EXTRA_STREAM, ArrayList(contentUris))
        }
        addFlags(FLAG_GRANT_READ_URI_PERMISSION)
        return start(context)
    }
}

/** Returns a content URI for this file via the app's FileProvider, usable in share intents. */
fun File.getFileUri(context: Context): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)
