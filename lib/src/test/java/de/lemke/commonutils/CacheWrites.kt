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
import java.io.File

/** The file of every cache write in [directoryName] of the cache directory, one per write directory. */
internal fun Context.cacheWriteFiles(directoryName: String): List<File> =
    File(cacheDir, directoryName).listFiles().orEmpty().flatMap { it.listFiles().orEmpty().asList() }

/** The name of the single write directory in [directoryName] of the cache directory. */
internal fun Context.cacheWriteDirectoryName(directoryName: String): String =
    File(cacheDir, directoryName)
        .listFiles()
        .orEmpty()
        .single()
        .name
