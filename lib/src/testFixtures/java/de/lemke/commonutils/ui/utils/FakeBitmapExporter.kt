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
import android.graphics.Bitmap
import android.net.Uri
import de.lemke.commonutils.data.SaveLocation
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [BitmapExporter] that writes nothing: each call records a [Call], waits for [gate] and returns the configured
 * result.
 */
public class FakeBitmapExporter : BitmapExporter {
    /**
     * The result of [saveToDirectory] for a location that needs no picker; null returns [BitmapSaveResult.Saved] to that
     * location. A location that [SaveLocation.needsPicker] always returns [BitmapSaveResult.NeedsPicker].
     */
    public var directoryResult: BitmapSaveResult.DirectoryResult? = null

    /** The result of [saveToCreatedDocument] for a non-null bitmap. */
    public var documentResult: BitmapSaveResult.UriResult = BitmapSaveResult.Saved(SaveLocation.CUSTOM)

    /** The result of [createClip]. */
    public var clip: ClipData? = null

    /** The result of [createShareFile]. */
    public var shareFile: BitmapShareFile = BitmapShareFile.Failed

    /** While set and not completed, every call suspends after it records its [Call]. */
    public var gate: CompletableDeferred<Unit>? = null

    /** While set, every call throws it after it records its [Call] and passes [gate]; it models a broken exporter. */
    public var exception: Exception? = null

    private val recordedCalls = mutableListOf<Call>()
    private val deletedUris = mutableListOf<Uri>()

    /** Every call in order, recorded before it waits for [gate]. */
    public val calls: List<Call> get() = recordedCalls.toList()

    /** The documents that [saveToCreatedDocument] or [deleteCreatedDocument] deleted. */
    public val deletedDocuments: List<Uri> get() = deletedUris.toList()

    override suspend fun saveToDirectory(
        location: SaveLocation,
        bitmap: Bitmap,
        fileName: String,
    ): BitmapSaveResult.DirectoryResult =
        record(Call.SaveToDirectory(location, bitmap, fileName)) {
            if (location.needsPicker) BitmapSaveResult.NeedsPicker else directoryResult ?: BitmapSaveResult.Saved(location)
        }

    override suspend fun saveToCreatedDocument(
        uri: Uri,
        bitmap: Bitmap?,
    ): BitmapSaveResult.UriResult =
        record(Call.SaveToCreatedDocument(uri, bitmap)) {
            val result = if (bitmap == null) BitmapSaveResult.WriteFailed else documentResult
            if (result !is BitmapSaveResult.Saved) deletedUris += uri
            result
        }

    override suspend fun deleteCreatedDocument(uri: Uri) {
        record(Call.DeleteCreatedDocument(uri)) { deletedUris += uri }
    }

    override suspend fun createClip(bitmap: Bitmap): ClipData? = record(Call.CreateClip(bitmap)) { clip }

    override suspend fun createShareFile(bitmap: Bitmap): BitmapShareFile = record(Call.CreateShareFile(bitmap)) { shareFile }

    private suspend fun <T> record(
        call: Call,
        result: () -> T,
    ): T {
        recordedCalls += call
        gate?.await()
        exception?.let { throw it }
        return result()
    }

    /** A recorded call to [FakeBitmapExporter]. */
    public sealed interface Call {
        /** A [FakeBitmapExporter.saveToDirectory] call. */
        public data class SaveToDirectory(
            val location: SaveLocation,
            val bitmap: Bitmap,
            val fileName: String,
        ) : Call

        /** A [FakeBitmapExporter.saveToCreatedDocument] call. */
        public data class SaveToCreatedDocument(
            val uri: Uri,
            val bitmap: Bitmap?,
        ) : Call

        /** A [FakeBitmapExporter.deleteCreatedDocument] call. */
        public data class DeleteCreatedDocument(
            val uri: Uri,
        ) : Call

        /** A [FakeBitmapExporter.createClip] call. */
        public data class CreateClip(
            val bitmap: Bitmap,
        ) : Call

        /** A [FakeBitmapExporter.createShareFile] call. */
        public data class CreateShareFile(
            val bitmap: Bitmap,
        ) : Call
    }
}
