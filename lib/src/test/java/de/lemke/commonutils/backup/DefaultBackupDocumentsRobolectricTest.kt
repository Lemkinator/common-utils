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
package de.lemke.commonutils.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.RecordingDocumentsProvider
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class NoStreamProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openAssetFile(
        uri: Uri,
        mode: String,
    ): AssetFileDescriptor? = null

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
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

private class OctetStreamProvider : ContentProvider() {
    lateinit var file: File

    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

private const val MAX_BYTES = 1024L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DefaultBackupDocumentsRobolectricTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val documents get() = DefaultBackupDocuments(context, Dispatchers.Unconfined)

    @Test
    fun `writes the bytes into the provider file`(): Unit =
        runBlocking {
            val provider = RecordingDocumentsProvider.create(context)

            documents.write(provider.uri, "zip bytes".encodeToByteArray()) shouldBe true

            provider.file.readText() shouldBe "zip bytes"
        }

    @Test
    fun `reads a document of exactly maxBytes and returns null for one byte more`(): Unit =
        runBlocking {
            val provider = RecordingDocumentsProvider.create(context)
            val bytes = ByteArray(20_000) { it.toByte() }
            documents.write(provider.uri, bytes) shouldBe true

            documents.read(provider.uri, 20_000)?.toList() shouldBe bytes.toList()
            documents.read(provider.uri, 19_999) shouldBe null
        }

    @Test
    fun `reads a document whose provider reports application-octet-stream`(): Unit =
        runBlocking {
            val uri = "content://octet.stream.documents/document/1".toUri()
            val provider = Robolectric.buildContentProvider(OctetStreamProvider::class.java).create("octet.stream.documents").get()
            provider.file = File(context.cacheDir, "backup.zip").apply { writeText("zip bytes") }
            context.contentResolver.getType(uri) shouldBe "application/octet-stream"

            documents.read(uri, MAX_BYTES)?.decodeToString() shouldBe "zip bytes"
        }

    @Test
    fun `returns null when the provider throws on open`(): Unit =
        runBlocking {
            val provider = RecordingDocumentsProvider.create(context)
            provider.file.delete()

            documents.read(provider.uri, MAX_BYTES) shouldBe null
        }

    @Test
    fun `returns null when the provider hands out no input stream`(): Unit =
        runBlocking {
            Robolectric.buildContentProvider(NoStreamProvider::class.java).create("no.stream.documents")

            documents.read("content://no.stream.documents/document/1".toUri(), MAX_BYTES) shouldBe null
        }

    @Test
    fun `returns false when the provider refuses the write`(): Unit =
        runBlocking {
            val provider = RecordingDocumentsProvider.create(context).apply { readOnly = true }

            documents.write(provider.uri, byteArrayOf(1)) shouldBe false
        }

    @Test
    fun `returns false when the output stream is null`(): Unit =
        runBlocking {
            Robolectric.buildContentProvider(NoStreamProvider::class.java).create("no.stream.documents")

            documents.write("content://no.stream.documents/document/1".toUri(), byteArrayOf(1)) shouldBe false
        }

    @Test
    fun `delete removes the document`(): Unit =
        runBlocking {
            val provider = RecordingDocumentsProvider.create(context)

            documents.delete(provider.uri)

            provider.deleted shouldContainExactly listOf(provider.uri)
            provider.file.exists() shouldBe false
        }
}
