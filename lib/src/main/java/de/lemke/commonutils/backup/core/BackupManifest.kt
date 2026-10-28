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
package de.lemke.commonutils.backup.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val BACKUP_KIND: String = "backup"
internal const val BACKUP_FORMAT: Int = 1
internal const val MANIFEST_ENTRY: String = "manifest.json"
internal const val SIGNATURE_ENTRY: String = "signature.json"

internal fun sectionEntryName(id: String): String = "sections/$id.json"

internal val backupJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/** The first entry of a backup file. [createdAt] is ISO-8601 text with a UTC offset; the core never parses it. */
@Serializable
internal data class BackupManifest(
    val kind: String,
    val format: Int,
    val app: String,
    val appVersionCode: Long,
    val createdAt: String,
    val sections: List<SectionInfo>,
)

@Serializable
internal data class SectionInfo(val id: String, val version: Int, val entries: Int)
