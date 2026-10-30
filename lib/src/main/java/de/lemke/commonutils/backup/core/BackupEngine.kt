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

/** What [BackupEngine.export] produced. */
public sealed interface BackupExport {
    /** The files of the backup by entry name. */
    public class Files(public val files: Map<String, ByteArray>) : BackupExport

    /** A section or the backup as a whole exceeds a limit of the import, so no file was produced. */
    public data object TooLarge : BackupExport
}

/**
 * Exports, reads and restores backups of [sections]. The core is pure Kotlin: [clock] returns the export time as
 * ISO-8601 text with a UTC offset, supplied by the platform layer. [sections] holds at least one section, and ids must
 * match [SECTION_ID]. An export that exceeds a limit of the import returns [BackupExport.TooLarge]. With a [signer], an
 * export is signed and a read refuses an unsigned file or one with a changed manifest or section file.
 */
public class BackupEngine(
    private val sections: Set<BackupSection<*>>,
    private val transaction: BackupTransaction,
    private val app: BackupApp,
    private val clock: () -> String,
    private val signer: BackupSigner? = null,
) {
    init {
        require(sections.isNotEmpty()) { "A backup needs at least one section" }
        require(sections.map { it.id }.toSet().size == sections.size) { "Backup section ids must be unique" }
        val invalid = sections.map { it.id }.filterNot { SECTION_ID.matches(it) }
        require(invalid.isEmpty()) { "Backup section ids must match ${SECTION_ID.pattern}: ${invalid.joinToString()}" }
    }

    /** The extension under which the app saves an export. */
    public val fileExtension: BackupFileExtension = app.fileExtension

    /** MERGE only when every bound section is a [MergeableSection]. */
    public val modes: Set<ImportMode> = importModes(sections)

    /**
     * The files of a backup by entry name: `manifest.json`, `sections/<id>.json` and, with a signer, `signature.json`.
     * All sections are read in one transaction.
     */
    public suspend fun export(): BackupExport = transaction.run { exportSections() }?.let { assemble(it) } ?: BackupExport.TooLarge

    private suspend fun assemble(exported: List<Pair<SectionInfo, ByteArray>>): BackupExport.Files? {
        val manifest = BackupManifest(BACKUP_KIND, BACKUP_FORMAT, app.id, app.versionCode, clock(), exported.map { it.first })
        val manifestBytes = backupJson.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray()
        val files =
            buildMap {
                put(MANIFEST_ENTRY, manifestBytes)
                exported.forEach { (info, bytes) -> put(sectionEntryName(info.id), bytes) }
            }
        val signature = signer?.let { signatureBytes(files, it) }
        val signed = if (signature == null) files else files + (SIGNATURE_ENTRY to signature)
        val withinLimits =
            manifestBytes.size <= MAX_MANIFEST_BYTES &&
                (signature?.size ?: 0) <= MAX_SIGNATURE_BYTES &&
                signed.values.sumOf { it.size.toLong() } <= MAX_TOTAL_BYTES
        return if (withinLimits) BackupExport.Files(signed) else null
    }

    private suspend fun exportSections(): List<Pair<SectionInfo, ByteArray>>? {
        val exported = mutableListOf<Pair<SectionInfo, ByteArray>>()
        var total = 0L
        for (section in sections) {
            val next = exportSection(section)
            if (next != null) total += next.second.size
            if (next == null || total > MAX_TOTAL_BYTES) return null
            exported += next
        }
        return exported
    }

    /**
     * Reads the backup in [files] and empties the map, whatever the result: each section file leaves it when its decode
     * starts, so the bytes of a finished section can be freed. Keep no other reference to the arrays.
     */
    public suspend fun read(files: MutableMap<String, ByteArray>): BackupReadResult = readBackup(files, sections, app, signer)

    public suspend fun restore(
        ready: BackupReadResult.Ready,
        mode: ImportMode,
    ): RestoreReport = restoreBackup(ready, mode, transaction)
}

private suspend fun <E : Any> exportSection(section: BackupSection<E>): Pair<SectionInfo, ByteArray>? {
    val entries = section.exportEntries()
    if (entries.size > MAX_SECTION_ENTRIES) return null
    val text = sectionFileText(entries.map { backupJson.encodeToJsonElement(section.serializer, it) })
    return text
        .encodeToByteArray()
        .takeIf { it.size <= MAX_SECTION_BYTES && it.withinJsonLimits() }
        ?.let { SectionInfo(section.id, section.version, entries.size) to it }
}
