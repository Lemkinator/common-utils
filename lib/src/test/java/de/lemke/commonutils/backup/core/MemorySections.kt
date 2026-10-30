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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class Item(val key: String, val value: Int = 0)

internal open class MemorySection(
    override val id: String = "items",
    override val version: Int = 1,
    override val minReadableVersion: Int = 1,
    val store: MutableList<Item> = mutableListOf(),
    private val upgradeStep: (Int, JsonObject) -> JsonObject = { _, entry -> entry },
    private val insertFailsFor: String? = null,
) : BackupSection<Item> {
    override val serializer: KSerializer<Item> = Item.serializer()

    override fun upgrade(
        fromVersion: Int,
        entry: JsonObject,
    ): JsonObject = upgradeStep(fromVersion, entry)

    override fun validate(entry: Item): EntryCheck = if (entry.value < 0) EntryCheck.Invalid("negative") else EntryCheck.Valid

    override suspend fun exportEntries(): List<Item> = store.toList()

    override suspend fun clear() {
        store.clear()
    }

    override suspend fun insert(entries: List<Item>) {
        store.addAll(entries)
        check(entries.none { it.key == insertFailsFor }) { "insert failed" }
    }
}

internal class MergeableMemorySection(
    id: String = "items",
    version: Int = 1,
    minReadableVersion: Int = 1,
    store: MutableList<Item> = mutableListOf(),
    upgradeStep: (Int, JsonObject) -> JsonObject = { _, entry -> entry },
    insertFailsFor: String? = null,
) : MemorySection(id, version, minReadableVersion, store, upgradeStep, insertFailsFor),
    MergeableSection<Item, String> {
    private val rule = newerWins<Item, Int>({ it.value }, compareBy { it.key })

    override fun key(entry: Item): String = entry.key

    override fun resolve(
        local: Item,
        incoming: Item,
    ): Item = rule(local, incoming)

    override suspend fun localEntries(keys: Set<String>): Map<String, Item> = store.filter { it.key in keys }.associateBy { it.key }

    override suspend fun replace(
        local: Item,
        merged: Item,
    ) {
        store[store.indexOf(local)] = merged
    }
}

internal val app = BackupApp("oneurl", 100, BackupFileExtension(".oneurl"))

internal fun entriesJson(vararg entries: String): String = """{"entries":[${entries.joinToString(",")}]}"""

internal fun item(
    key: String,
    value: Int = 0,
): String = """{"key":"$key","value":$value}"""

internal fun backupFilesOf(
    vararg sections: Pair<SectionInfo, String>,
    appId: String = "oneurl",
    kind: String = BACKUP_KIND,
    format: Int = BACKUP_FORMAT,
): Map<String, ByteArray> {
    val manifest = BackupManifest(kind, format, appId, 7, "2026-10-27T14:30:00+01:00", sections.map { it.first })
    return buildMap {
        put(MANIFEST_ENTRY, backupJson.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray())
        sections.forEach { (info, text) -> put(sectionEntryName(info.id), text.encodeToByteArray()) }
    }
}
