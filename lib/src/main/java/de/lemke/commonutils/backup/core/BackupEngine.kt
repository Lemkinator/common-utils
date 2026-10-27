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

/**
 * Exports, reads and restores backups of [sections]. The core is pure Kotlin: [clock] returns the export time as
 * ISO-8601 text with a UTC offset, supplied by the platform layer.
 */
public class BackupEngine(
    private val sections: Set<BackupSection<*>>,
    private val transaction: BackupTransaction,
    private val app: BackupApp,
    private val clock: () -> String,
) {
    init {
        require(sections.map { it.id }.toSet().size == sections.size) { "Backup section ids must be unique" }
    }

    /** MERGE only when every bound section is a [MergeableSection]. */
    public val modes: Set<ImportMode> = importModes(sections)

    /** The files of a backup by entry name: `manifest.json` and `sections/<id>.json`. All sections are read in one transaction. */
    public suspend fun export(): Map<String, ByteArray> {
        val exported = transaction.run { sections.map { exportSection(it) } }
        val manifest = BackupManifest(BACKUP_KIND, BACKUP_FORMAT, app.id, app.versionCode, clock(), exported.map { it.first })
        return buildMap {
            put(MANIFEST_ENTRY, backupJson.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray())
            exported.forEach { (info, bytes) -> put(sectionEntryName(info.id), bytes) }
        }
    }

    public fun read(files: Map<String, ByteArray>): BackupReadResult = readBackup(files, sections, app)

    public suspend fun restore(
        ready: BackupReadResult.Ready,
        mode: ImportMode,
    ): RestoreReport = restoreBackup(ready, mode, transaction)
}

private suspend fun <E : Any> exportSection(section: BackupSection<E>): Pair<SectionInfo, ByteArray> {
    val entries = section.exportEntries()
    val file = SectionFile(entries.map { backupJson.encodeToJsonElement(section.serializer, it) })
    return SectionInfo(section.id, section.version, entries.size) to
        backupJson.encodeToString(SectionFile.serializer(), file).encodeToByteArray()
}
