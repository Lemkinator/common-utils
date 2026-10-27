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

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Checks the manifest, then the version of every known section, then decodes every entry. A refusal returns before any
 * decode. A [BackupSection.upgrade] that cannot map an entry must throw an [IllegalArgumentException]; the entry is skipped.
 */
internal fun readBackup(
    files: Map<String, ByteArray>,
    sections: Set<BackupSection<*>>,
    app: BackupApp,
): BackupReadResult {
    val manifest = files[MANIFEST_ENTRY]?.let(::decodeManifest) ?: return BackupReadResult.Refused(BackupRefusal.NotABackup)
    val known = sections.associateBy { it.id }
    val present = manifest.sections.mapNotNull { info -> known[info.id]?.let { info to it } }
    val refusal = manifestRefusal(manifest, app) ?: present.firstNotNullOfOrNull { (info, section) -> versionRefusal(info, section) }
    return if (refusal != null) BackupReadResult.Refused(refusal) else readSections(manifest, present, known.keys, files)
}

private fun readSections(
    manifest: BackupManifest,
    present: List<Pair<SectionInfo, BackupSection<*>>>,
    knownIds: Set<String>,
    files: Map<String, ByteArray>,
): BackupReadResult {
    val read = present.mapNotNull { (info, section) -> readSection(info, section, files) }
    if (read.size < present.size) return BackupReadResult.Refused(BackupRefusal.NotABackup)
    val preview =
        BackupPreview(
            createdAt = manifest.createdAt,
            appVersionCode = manifest.appVersionCode,
            sections = read.map { SectionPreview(it.section.id, it.entries.size, it.skipped) },
            skippedSections = manifest.sections.map { it.id }.filter { it !in knownIds },
            modes = importModes(read.map { it.section }),
        )
    return BackupReadResult.Ready(preview, read)
}

private fun decodeManifest(bytes: ByteArray): BackupManifest? =
    decodeOrNull {
        backupJson.decodeFromString(BackupManifest.serializer(), bytes.decodeToString())
    }

private fun decodeSectionFile(bytes: ByteArray): SectionFile? =
    decodeOrNull {
        backupJson.decodeFromString(SectionFile.serializer(), bytes.decodeToString())
    }

private inline fun <T> decodeOrNull(decode: () -> T): T? =
    try {
        decode()
    } catch (_: IllegalArgumentException) {
        null
    }

private fun manifestRefusal(
    manifest: BackupManifest,
    app: BackupApp,
): BackupRefusal? =
    when {
        manifest.kind != BACKUP_KIND -> BackupRefusal.NotABackup
        manifest.app != app.id -> BackupRefusal.OtherApp(manifest.app)
        manifest.format > BACKUP_FORMAT -> BackupRefusal.NewerFormat(manifest.format)
        manifest.sections
            .map { it.id }
            .toSet()
            .size != manifest.sections.size -> BackupRefusal.NotABackup
        else -> null
    }

private fun versionRefusal(
    info: SectionInfo,
    section: BackupSection<*>,
): BackupRefusal? =
    when {
        info.version > section.version -> BackupRefusal.NewerSection(info.id, info.version)
        info.version < section.minReadableVersion -> BackupRefusal.TooOldSection(info.id, info.version)
        else -> null
    }

private fun <E : Any> readSection(
    info: SectionInfo,
    section: BackupSection<E>,
    files: Map<String, ByteArray>,
): ReadSection<E>? {
    val file = files[sectionEntryName(info.id)]?.let(::decodeSectionFile) ?: return null
    val decoded = file.entries.map { decodeEntry(it, info.version, section) }
    return ReadSection(section, decoded.filterNotNull(), decoded.count { it == null })
}

private fun <E : Any> decodeEntry(
    element: JsonElement,
    fromVersion: Int,
    section: BackupSection<E>,
): E? {
    val raw = element as? JsonObject ?: return null
    return decodeOrNull {
        val upgraded = (fromVersion until section.version).fold(raw) { entry, version -> section.upgrade(version, entry) }
        backupJson.decodeFromJsonElement(section.serializer, upgraded).takeIf { section.validate(it) == EntryCheck.Valid }
    }
}
