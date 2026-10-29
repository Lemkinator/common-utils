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

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Checks the manifest and, for a [signer], the signature, then the version of every known section, then decodes every
 * entry. A refusal returns before any decode. An entry whose [BackupSection.upgrade], decoding or
 * [BackupSection.validate] throws is skipped and counted. The decode checks the caller's job before each entry, so a
 * cancellation stops a large section.
 */
internal suspend fun readBackup(
    files: Map<String, ByteArray>,
    sections: Set<BackupSection<*>>,
    app: BackupApp,
    signer: BackupSigner?,
): BackupReadResult {
    val manifest = files[MANIFEST_ENTRY]?.let(::decodeManifest) ?: return BackupReadResult.Refused(BackupRefusal.NotABackup)
    val known = sections.associateBy { it.id }
    val present = manifest.sections.mapNotNull { info -> known[info.id]?.let { info to it } }
    val refusal =
        manifestRefusal(manifest, app)
            ?: signer?.let { signatureRefusal(files, it) }
            ?: present.firstNotNullOfOrNull { (info, section) -> versionRefusal(info, section) }
            ?: BackupRefusal.NoKnownSection.takeIf { present.isEmpty() }
    return if (refusal != null) BackupReadResult.Refused(refusal) else readSections(manifest, present, known.keys, files)
}

private suspend fun readSections(
    manifest: BackupManifest,
    present: List<Pair<SectionInfo, BackupSection<*>>>,
    knownIds: Set<String>,
    files: Map<String, ByteArray>,
): BackupReadResult {
    val read = mutableListOf<ReadSection<*>>()
    for ((info, section) in present) {
        when (val outcome = readSection(info, section, files)) {
            is SectionRead.Read -> read += outcome.section
            is SectionRead.Refused -> return BackupReadResult.Refused(outcome.reason)
        }
    }
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

private fun decodeManifest(bytes: ByteArray): BackupManifest? = bytes.decodeWithinLimits(BackupManifest.serializer())

// App upgrade, validate and serializer code throws an open-ended exception set; one broken entry must not fail the import.
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> decodeOrNull(decode: () -> T): T? =
    try {
        decode()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
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

private sealed interface SectionRead {
    class Read(val section: ReadSection<*>) : SectionRead

    class Refused(val reason: BackupRefusal) : SectionRead
}

private suspend fun <E : Any> readSection(
    info: SectionInfo,
    section: BackupSection<E>,
    files: Map<String, ByteArray>,
): SectionRead {
    val bytes = files[sectionEntryName(info.id)]
    return when {
        bytes == null -> SectionRead.Refused(BackupRefusal.NotABackup)
        !bytes.withinJsonLimits() -> SectionRead.Refused(BackupRefusal.TooLarge)
        else -> decodeSection(bytes, info, section) ?: SectionRead.Refused(BackupRefusal.NotABackup)
    }
}

private suspend fun <E : Any> decodeSection(
    bytes: ByteArray,
    info: SectionInfo,
    section: BackupSection<E>,
): SectionRead? {
    val context = currentCoroutineContext()
    return decodeOrNull {
        try {
            val decoder = SectionDecoder(info.version, section) { context.ensureActive() }
            SectionRead.Read(backupJson.decodeFromString(decoder, bytes.decodeToString()))
        } catch (_: TooManyEntriesException) {
            SectionRead.Refused(BackupRefusal.TooLarge)
        } catch (e: SerializationException) {
            // The JSON decoder wraps what an upgrade step throws; a cancellation must still propagate.
            throw e.cause as? CancellationException ?: e
        }
    }
}

internal fun <E : Any> decodeEntry(
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
