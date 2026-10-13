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
package de.lemke.commonutils

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Matches the content URI of the file [fileName] in a cache write: the app's FileProvider authority
 * (`<package>.fileprovider`), the FileProvider path name [pathName] of the cache directory, the cache subdirectory
 * [directoryName] of the write's kind (`share` or `clipboard`) and one write directory.
 */
fun Context.cacheWriteUriPattern(
    pathName: String,
    directoryName: String,
    fileName: String,
): Regex =
    Regex(
        Regex.escape("content://$packageName.fileprovider/$pathName/$directoryName/commonutils-") +
            """\d+/""" +
            Regex.escape(fileName),
    )

/** The cache file that [contentUri] stands for: its path below the FileProvider path name, in the cache directory. */
fun Context.cacheFileOf(contentUri: Uri): File = File(cacheDir, contentUri.pathSegments.drop(1).joinToString("/"))
