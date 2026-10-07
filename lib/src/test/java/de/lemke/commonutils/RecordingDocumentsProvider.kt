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

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.os.BundleCompat
import java.io.File
import org.robolectric.Robolectric

/** A documents provider whose single document is a cache file; it records each document it is asked to delete. */
class RecordingDocumentsProvider : ContentProvider() {
    lateinit var file: File
        private set
    var deleteFailure: Exception? = null
    val deleted = mutableListOf<Uri>()
    val uri: Uri = Uri.parse("content://$AUTHORITY/document/1")

    override fun onCreate() = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        if (method == METHOD_DELETE_DOCUMENT) {
            deleted += BundleCompat.getParcelable(extras!!, EXTRA_DOCUMENT_URI, Uri::class.java)!!
            deleteFailure?.let { throw it }
        }
        return null
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    companion object {
        const val AUTHORITY = "de.lemke.commonutils.test.documents"

        // The hidden DocumentsContract.METHOD_DELETE_DOCUMENT and EXTRA_URI that deleteDocument sends to the provider.
        private const val METHOD_DELETE_DOCUMENT = "android:deleteDocument"
        private const val EXTRA_DOCUMENT_URI = "uri"

        fun create(context: Context): RecordingDocumentsProvider =
            Robolectric
                .buildContentProvider(RecordingDocumentsProvider::class.java)
                .create(AUTHORITY)
                .get()
                .apply { file = File(context.cacheDir, "document.png").also { it.createNewFile() } }
    }
}
