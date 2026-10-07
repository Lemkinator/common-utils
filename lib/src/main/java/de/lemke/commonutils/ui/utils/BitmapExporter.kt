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
import android.net.Uri
import de.lemke.commonutils.data.SaveLocation
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Exports a bitmap: saves it to a public directory or a picked document, and writes it for the clipboard or a share.
 *
 * A ViewModel runs these main-safe calls in `viewModelScope` and exposes each result as UI state. Tests replace it with
 * `FakeBitmapExporter` from the testFixtures.
 */
interface BitmapExporter {
    /**
     * Writes [bitmap] as a PNG named after [fileName] to the public directory of [location].
     *
     * A location that [SaveLocation.needsPicker] writes nothing and returns [BitmapSaveResult.NeedsPicker].
     */
    suspend fun saveToDirectory(
        location: SaveLocation,
        bitmap: Bitmap,
        fileName: String,
    ): BitmapSaveResult.DirectoryResult

    /**
     * Writes [bitmap] as a PNG to [uri], a document the `ACTION_CREATE_DOCUMENT` picker created.
     *
     * Deletes the created document unless [bitmap] is written to it; a null [bitmap] returns
     * [BitmapSaveResult.WriteFailed].
     */
    suspend fun saveToCreatedDocument(
        uri: Uri,
        bitmap: Bitmap?,
    ): BitmapSaveResult.UriResult

    /** Writes [bitmap] as a PNG cache file and returns a clip of its content URI, or null if writing fails. */
    suspend fun createClip(bitmap: Bitmap): ClipData?

    /** Writes [bitmap] as a PNG cache file for a share. */
    suspend fun createShareFile(bitmap: Bitmap): BitmapShareFile
}

/**
 * The [BitmapExporter] over [saveBitmapToDirectory], [saveBitmapToUri], [createBitmapClip] and [createBitmapShareFile],
 * which run their work on [ioDispatcher].
 *
 * The library publishes no DI bindings, so an app provides it from its own module with an application [context] and its
 * `@IoDispatcher`. Clips carry [clipLabel]; clip and share cache files are named [fileName].
 */
class DefaultBitmapExporter(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val clipLabel: String,
    private val fileName: String,
) : BitmapExporter {
    override suspend fun saveToDirectory(
        location: SaveLocation,
        bitmap: Bitmap,
        fileName: String,
    ): BitmapSaveResult.DirectoryResult = saveBitmapToDirectory(location, bitmap, fileName, ioDispatcher)

    override suspend fun saveToCreatedDocument(
        uri: Uri,
        bitmap: Bitmap?,
    ): BitmapSaveResult.UriResult = context.saveBitmapToUri(uri, bitmap, createdDocument = true, ioDispatcher)

    override suspend fun createClip(bitmap: Bitmap): ClipData? = context.createBitmapClip(bitmap, clipLabel, fileName, ioDispatcher)

    override suspend fun createShareFile(bitmap: Bitmap): BitmapShareFile = context.createBitmapShareFile(bitmap, fileName, ioDispatcher)
}
