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

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.ui.utils.deleteDocument
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "BackupDocuments"

/**
 * Reads and writes the documents of a backup. A [BackupStateHolder] runs these main-safe calls; tests replace it with
 * `FakeBackupDocuments` from the testFixtures.
 *
 * Contract: an implementation reports a provider failure as `false` or `null` and never throws, except
 * [kotlinx.coroutines.CancellationException].
 */
public interface BackupDocuments {
    /** Writes [bytes] to [uri], a document the `ACTION_CREATE_DOCUMENT` picker created; false if the provider refuses. */
    public suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    ): Boolean

    /**
     * Reads the whole document at [uri], whatever MIME type its provider reports; null on any provider failure and for a
     * document larger than [maxBytes], which it stops reading at that size.
     */
    public suspend fun read(
        uri: Uri,
        maxBytes: Long,
    ): ByteArray?

    /** Deletes the document at [uri]; a failed delete only logs. */
    public suspend fun delete(uri: Uri)
}

/** The [BackupDocuments] over the content resolver of the application context; every call runs on [io]. */
public class DefaultBackupDocuments @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BackupDocuments {
    // Providers and system services throw an open-ended exception set; every failure must return a result, not crash.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    ): Boolean =
        withContext(io) {
            try {
                val output = context.contentResolver.openOutputStream(uri)
                if (output == null) {
                    false
                } else {
                    output.use { it.write(bytes) }
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing backup", e)
                false
            }
        }

    // Providers and system services throw an open-ended exception set; every failure must return a result, not crash.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(
        uri: Uri,
        maxBytes: Long,
    ): ByteArray? =
        withContext(io) {
            try {
                context.contentResolver.openInputStream(uri)?.use { it.readAtMost(maxBytes) }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading backup", e)
                null
            }
        }

    override suspend fun delete(uri: Uri) {
        withContext(io) { context.deleteDocument(uri) }
    }
}
