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

import android.net.Uri

/**
 * A [BackupDocuments] over memory. [files] holds the written documents. While [failWrite] is true, [write] stores
 * nothing and returns false; while [failRead] is true, [read] returns null, as it does for a document larger than
 * `maxBytes`. [deleted] lists each [delete], in order.
 */
public class FakeBackupDocuments : BackupDocuments {
    public val files: MutableMap<Uri, ByteArray> = mutableMapOf()
    public var failWrite: Boolean = false
    public var failRead: Boolean = false
    private val deletedUris = mutableListOf<Uri>()
    public val deleted: List<Uri> get() = deletedUris

    override suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    ): Boolean {
        if (!failWrite) files[uri] = bytes
        return !failWrite
    }

    override suspend fun read(
        uri: Uri,
        maxBytes: Long,
    ): ByteArray? = files[uri].takeUnless { failRead }?.takeIf { it.size <= maxBytes }

    override suspend fun delete(uri: Uri) {
        files.remove(uri)
        deletedUris += uri
    }
}
