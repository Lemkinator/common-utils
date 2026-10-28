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
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.backup.BackupResult
import de.lemke.commonutils.backup.core.BackupPreview
import de.lemke.commonutils.backup.core.BackupRefusal
import de.lemke.commonutils.backup.core.ImportMode
import de.lemke.commonutils.backup.core.RestoreReport
import de.lemke.commonutils.backup.core.SectionPreview
import de.lemke.commonutils.backup.core.SectionReport
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun preview(
    createdAt: String = "2026-10-27T14:30:00+01:00",
    sections: List<SectionPreview> = listOf(SectionPreview("urls", 1, 0)),
    skippedSections: List<String> = emptyList(),
    modes: Set<ImportMode> = setOf(ImportMode.REPLACE),
) = BackupPreview(createdAt, 7, sections, skippedSections, modes)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupTextsRobolectricTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a preview names the date, the entries of each section and nothing else for a replace-only backup`() {
        val message = context.backupPreviewMessage(preview(sections = listOf(SectionPreview("urls", 1, 0), SectionPreview("notes", 3, 0))))

        message shouldStartWith "Backup from "
        message shouldContain "2026"
        message.substringAfter("\n\n") shouldBe "urls: 1 entry\nnotes: 3 entries"
    }

    @Test
    fun `a preview counts skipped entries per section`() {
        context.backupPreviewMessage(preview(sections = listOf(SectionPreview("urls", 2, 3)))) shouldContain
            "urls: 2 entries, 3 broken entries skipped"
    }

    @Test
    fun `a preview of Merge and Replace shows the modes text`() {
        context.backupPreviewMessage(preview(modes = ImportMode.entries.toSet())) shouldContain
            "Merge adds the backup to your data. Entries you deleted since then come back. Replace swaps all data for the backup."
    }

    @Test
    fun `a preview joins the skipped sections with commas`() {
        context.backupPreviewMessage(preview(skippedSections = listOf("a", "b"))) shouldContain
            "This backup holds parts this app version does not know: a, b. They are skipped."
    }

    @Test
    fun `a preview shows an unparsable date as written`() {
        context.backupPreviewMessage(preview(createdAt = "yesterday")) shouldStartWith "Backup from yesterday"
    }

    @Test
    fun `a backup without sections has a preview with the date only`() {
        context.backupPreviewMessage(preview(sections = emptyList())).lines().size shouldBe 1
    }

    @Test
    fun `result messages read as the owner texts`() {
        val merge = RestoreReport(ImportMode.MERGE, listOf(SectionReport("a", 1, 2, 3, 0, 4), SectionReport("b", 10, 20, 30, 0, 40)))
        val replace = RestoreReport(ImportMode.REPLACE, listOf(SectionReport("a", 5, 0, 0, 6, 7)))

        context.backupResultMessage(BackupResult.Exported) shouldBe "Backup exported"
        context.backupResultMessage(BackupResult.ExportFailed) shouldBe "Error exporting backup"
        context.backupResultMessage(BackupResult.Restored(merge)) shouldBe
            "Backup imported: 11 added, 22 updated, 33 kept, 44 broken entries skipped."
        context.backupResultMessage(BackupResult.Restored(replace)) shouldBe
            "Backup imported: 5 entries restored, 6 previous entries removed, 7 broken entries skipped."
        context.backupResultMessage(BackupResult.ReadFailed) shouldBe "The file could not be read."
        context.backupResultMessage(BackupResult.RestoreFailed) shouldBe "Error importing backup. Your data is unchanged."
    }

    @Test
    fun `refusal messages tell what to do`() {
        context.backupRefusalMessage(BackupRefusal.NotABackup) shouldBe "This file is not a backup."
        context.backupRefusalMessage(BackupRefusal.OtherApp("sudoku")) shouldBe "This backup belongs to another app."
        context.backupRefusalMessage(BackupRefusal.NewerFormat(2)) shouldBe
            "This backup was made with a newer version of the app. Please update the app."
        context.backupRefusalMessage(BackupRefusal.NewerSection("urls", 3)) shouldBe
            "This backup was made with a newer version of the app. Please update the app."
        context.backupRefusalMessage(BackupRefusal.TooOldSection("urls", 1)) shouldBe "This backup is too old to be imported."
        context.backupRefusalMessage(BackupRefusal.NoKnownSection) shouldBe "This backup holds no data this app version knows."
        context.backupRefusalMessage(BackupRefusal.TooLarge) shouldBe "The file could not be read."
        context.backupRefusalMessage(BackupRefusal.Tampered) shouldBe "This backup was changed outside the app."
        context.backupResultMessage(BackupResult.Refused(BackupRefusal.NotABackup)) shouldBe "This file is not a backup."
    }
}
