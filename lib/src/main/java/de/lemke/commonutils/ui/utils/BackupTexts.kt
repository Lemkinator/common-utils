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
package de.lemke.commonutils.ui.utils

import android.content.Context
import de.lemke.commonutils.R
import de.lemke.commonutils.backup.BackupResult
import de.lemke.commonutils.backup.core.BackupPreview
import de.lemke.commonutils.backup.core.BackupRefusal
import de.lemke.commonutils.backup.core.ImportMode
import de.lemke.commonutils.backup.core.RestoreReport
import de.lemke.commonutils.backup.core.SectionPreview
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/** The message of the import preview: backup date, entries per section, skipped parts, and the modes text if Merge is offered. */
internal fun Context.backupPreviewMessage(preview: BackupPreview): String =
    listOf(
        getString(R.string.commonutils_backup_preview_date, backupDate(preview.createdAt)),
        preview.sections.joinToString("\n") { sectionLine(it) },
        if (preview.skippedSections.isEmpty()) {
            ""
        } else {
            getString(R.string.commonutils_backup_skipped_sections, preview.skippedSections.joinToString(", "))
        },
        if (ImportMode.MERGE in preview.modes) getString(R.string.commonutils_backup_modes_info) else "",
    ).filter { it.isNotEmpty() }.joinToString("\n\n")

/** The message that tells how an export or import ended. */
internal fun Context.backupResultMessage(result: BackupResult): String =
    when (result) {
        BackupResult.Exported -> getString(R.string.commonutils_backup_exported)
        BackupResult.ExportFailed -> getString(R.string.commonutils_backup_export_failed)
        is BackupResult.Restored -> restoredMessage(result.report)
        is BackupResult.Refused -> backupRefusalMessage(result.reason)
        BackupResult.ReadFailed -> getString(R.string.commonutils_backup_read_failed)
        BackupResult.RestoreFailed -> getString(R.string.commonutils_backup_restore_failed)
    }

/** The message that tells why a file imports nowhere. */
internal fun Context.backupRefusalMessage(reason: BackupRefusal): String =
    getString(
        when (reason) {
            BackupRefusal.NotABackup -> R.string.commonutils_backup_refused_not_a_backup
            is BackupRefusal.OtherApp -> R.string.commonutils_backup_refused_other_app
            is BackupRefusal.NewerFormat, is BackupRefusal.NewerSection -> R.string.commonutils_backup_refused_newer
            is BackupRefusal.TooOldSection -> R.string.commonutils_backup_refused_too_old
            BackupRefusal.NoKnownSection -> R.string.commonutils_backup_refused_no_known_section
            BackupRefusal.TooLarge -> R.string.commonutils_backup_read_failed
            BackupRefusal.Tampered -> R.string.commonutils_backup_refused_tampered
        },
    )

// A manifest is untrusted input: an unparsable date shows as written instead of failing the preview.
private fun Context.backupDate(createdAt: String): String =
    try {
        val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(resources.configuration.locales[0])
        OffsetDateTime.parse(createdAt).format(format)
    } catch (_: DateTimeParseException) {
        createdAt
    }

private fun Context.sectionLine(section: SectionPreview): String {
    val entries = count(R.plurals.commonutils_backup_entries, section.entries)
    return if (section.skipped == 0) {
        getString(R.string.commonutils_backup_section_line, section.id, entries)
    } else {
        val skipped = count(R.plurals.commonutils_backup_entries_skipped, section.skipped)
        getString(R.string.commonutils_backup_section_line_skipped, section.id, entries, skipped)
    }
}

private fun Context.restoredMessage(report: RestoreReport): String {
    val sections = report.sections
    val skipped = sections.sumOf { it.skipped }
    val parts =
        when (report.mode) {
            ImportMode.MERGE ->
                listOf(
                    count(R.plurals.commonutils_backup_added, sections.sumOf { it.added }),
                    count(R.plurals.commonutils_backup_updated, sections.sumOf { it.updated }),
                    count(R.plurals.commonutils_backup_kept, sections.sumOf { it.kept }),
                )
            ImportMode.REPLACE ->
                listOf(
                    count(R.plurals.commonutils_backup_restored_entries, sections.sumOf { it.added }),
                    count(R.plurals.commonutils_backup_removed_entries, sections.sumOf { it.removed }),
                )
        } + count(R.plurals.commonutils_backup_entries_skipped, skipped)
    return getString(R.string.commonutils_backup_restored, parts.joinToString(", "))
}

private fun Context.count(
    plural: Int,
    quantity: Int,
): String = resources.getQuantityString(plural, quantity, quantity)
