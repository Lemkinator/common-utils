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

import de.lemke.commonutils.backup.core.BackupApp
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.backup.core.BackupReadResult
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.EntryCheck
import de.lemke.commonutils.backup.core.ImportMode
import de.lemke.commonutils.backup.core.MAX_SECTION_ENTRIES
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
private data class Wide(
    val a: Int,
    val b: Int,
    val c: Int,
    val d: Int,
    val e: Int,
    val f: Int,
    val g: Int,
    val h: Int,
    val i: Int,
    val j: Int,
    val k: Int,
    val l: Int,
    val m: Int,
    val n: Int,
    val o: Int,
    val p: Int,
    val q: Int,
    val r: Int,
    val s: Int,
    val t: Int,
)

private fun wide(seed: Int) = Wide(seed, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)

private class WideSection(val store: MutableList<Wide> = mutableListOf()) : BackupSection<Wide> {
    override val id: String = "wide"
    override val serializer: KSerializer<Wide> = Wide.serializer()
    override val version: Int = 1
    override val minReadableVersion: Int = 1

    override fun upgrade(
        fromVersion: Int,
        entry: JsonObject,
    ): JsonObject = entry

    override fun validate(entry: Wide): EntryCheck = EntryCheck.Valid

    override suspend fun exportEntries(): List<Wide> = store.toList()

    override suspend fun clear() = store.clear()

    override suspend fun insert(entries: List<Wide>) {
        store += entries
    }
}

class LargeBackupTest : ShouldSpec(
    {
        should("export, zip, unzip, read and restore a section of the most entries with twenty fields each") {
            val exporting = WideSection(MutableList(MAX_SECTION_ENTRIES, ::wide))
            val exporter = BackupEngine(setOf(exporting), DirectBackupTransaction(), BackupApp("wide", 1), { "2026-10-27T14:30:00+01:00" })
            val zip = ByteArrayOutputStream().also { ZipBackupContainer.write(exporter.exportFiles(), it) }.toByteArray()

            val files = ZipBackupContainer.read(ByteArrayInputStream(zip)).shouldBeInstanceOf<ZipRead.Files>().files
            val importing = WideSection()
            val importer = BackupEngine(setOf(importing), DirectBackupTransaction(), BackupApp("wide", 1), { "2026-10-27T14:30:00+01:00" })
            val ready = importer.read(files).shouldBeInstanceOf<BackupReadResult.Ready>()
            importer.restore(ready, ImportMode.REPLACE)

            ready.preview.sections
                .single()
                .skipped shouldBe 0
            importing.store shouldHaveSize MAX_SECTION_ENTRIES
            importing.store shouldBe exporting.store
        }
    },
)
