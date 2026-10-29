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
 * Applies [mode] to every section of [ready] inside one [transaction]. A throw rolls every section back; MERGE on a
 * section without merge rules throws an [IllegalArgumentException].
 */
internal suspend fun restoreBackup(
    ready: BackupReadResult.Ready,
    mode: ImportMode,
    transaction: BackupTransaction,
): RestoreReport {
    val reports =
        transaction.run {
            ready.sections.map { read ->
                when (mode) {
                    ImportMode.REPLACE -> replaceSection(read)
                    ImportMode.MERGE -> mergeSection(read)
                }
            }
        }
    return RestoreReport(mode, reports)
}

private suspend fun <E : Any> replaceSection(read: ReadSection<E>): SectionReport {
    val removed = read.section.exportEntries().size
    read.section.clear()
    read.section.insert(read.entries)
    return SectionReport(read.section.id, added = read.entries.size, updated = 0, kept = 0, removed = removed, skipped = read.skipped)
}

private suspend fun <E : Any> mergeSection(read: ReadSection<E>): SectionReport {
    val section = read.section
    require(section is MergeableSection<E, *>) { "Merge needs a MergeableSection: ${section.id}" }
    return mergeEntries(section, read.entries, read.skipped)
}

private suspend fun <E : Any, K : Any> mergeEntries(
    section: MergeableSection<E, K>,
    entries: List<E>,
    skipped: Int,
): SectionReport {
    val incoming = linkedMapOf<K, E>()
    entries.forEach { entry ->
        val key = section.key(entry)
        val earlier = incoming[key]
        incoming[key] = if (earlier == null) entry else section.resolve(earlier, entry)
    }
    val local = section.localEntries(incoming.keys)
    val (existing, fresh) = incoming.entries.partition { it.key in local }
    section.insert(fresh.map { it.value })
    val changed =
        existing.mapNotNull { (key, entry) ->
            val current = local.getValue(key)
            val merged = section.resolve(current, entry)
            (current to merged).takeIf { merged != current }
        }
    changed.forEach { (current, merged) -> section.replace(current, merged) }
    return SectionReport(
        section.id,
        added = fresh.size,
        updated = changed.size,
        kept = existing.size - changed.size,
        removed = 0,
        skipped = skipped,
    )
}
