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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private class SnapshotTransaction(private vararg val stores: MutableList<Item>) : BackupTransaction {
    override suspend fun <R> run(block: suspend () -> R): R {
        val snapshots = stores.map { it.toList() }
        var committed = false
        try {
            return block().also { committed = true }
        } finally {
            if (!committed) stores.zip(snapshots).forEach { (store, snapshot) -> store.clear().also { store.addAll(snapshot) } }
        }
    }
}

private suspend fun readyOf(
    files: Map<String, ByteArray>,
    vararg sections: BackupSection<*>,
): BackupReadResult.Ready = readBackup(files.toMutableMap(), sections.toSet(), app, null).shouldBeInstanceOf<BackupReadResult.Ready>()

private fun info(
    id: String = "items",
    entries: Int = 0,
) = SectionInfo(id, 1, entries)

class BackupRestorerTest : ShouldSpec(
    {
        should("replace wipes local entries and inserts the file") {
            val store = mutableListOf(Item("x"), Item("y"), Item("z"))
            val section = MemorySection(store = store)
            val files = backupFilesOf(info() to entriesJson(item("a"), item("b")))
            val report = restoreBackup(readyOf(files, section), ImportMode.REPLACE, SnapshotTransaction(store))
            store shouldBe listOf(Item("a"), Item("b"))
            report shouldBe
                RestoreReport(
                    ImportMode.REPLACE,
                    listOf(SectionReport("items", added = 2, updated = 0, kept = 0, removed = 3, skipped = 0)),
                )
        }
        should("replace keeps a bound section that the file lacks") {
            val items = mutableListOf(Item("x"))
            val other = mutableListOf(Item("keep"))
            val files = backupFilesOf(info() to entriesJson(item("a")))
            val ready = readyOf(files, MemorySection(store = items), MemorySection(id = "other", store = other))
            restoreBackup(ready, ImportMode.REPLACE, SnapshotTransaction(items, other))
            items shouldBe listOf(Item("a"))
            other shouldBe listOf(Item("keep"))
        }
        should("replace rolls back every section when one insert throws") {
            val first = mutableListOf(Item("first-old"))
            val second = mutableListOf(Item("second-old"))
            val files = backupFilesOf(info("first") to entriesJson(item("first-new")), info("second") to entriesJson(item("boom")))
            val ready =
                readyOf(
                    files,
                    MemorySection(id = "first", store = first),
                    MemorySection(id = "second", store = second, insertFailsFor = "boom"),
                )
            shouldThrow<IllegalStateException> { restoreBackup(ready, ImportMode.REPLACE, SnapshotTransaction(first, second)) }
            first shouldBe listOf(Item("first-old"))
            second shouldBe listOf(Item("second-old"))
        }
        should("merge adds new keys and keeps local-only entries") {
            val store = mutableListOf(Item("local", 1))
            val files = backupFilesOf(info() to entriesJson(item("incoming", 2)))
            val report = restoreBackup(readyOf(files, MergeableMemorySection(store = store)), ImportMode.MERGE, SnapshotTransaction(store))
            store shouldBe listOf(Item("local", 1), Item("incoming", 2))
            report.sections.single() shouldBe SectionReport("items", added = 1, updated = 0, kept = 0, removed = 0, skipped = 0)
        }
        should("merge replaces an entry when resolve returns a different entry") {
            val store = mutableListOf(Item("a", 1))
            val files = backupFilesOf(info() to entriesJson(item("a", 5)))
            val report = restoreBackup(readyOf(files, MergeableMemorySection(store = store)), ImportMode.MERGE, SnapshotTransaction(store))
            store shouldBe listOf(Item("a", 5))
            report.sections.single() shouldBe SectionReport("items", added = 0, updated = 1, kept = 0, removed = 0, skipped = 0)
        }
        should("merge counts kept when resolve returns the local entry") {
            val store = mutableListOf(Item("a", 9))
            val files = backupFilesOf(info() to entriesJson(item("a", 5)))
            val report = restoreBackup(readyOf(files, MergeableMemorySection(store = store)), ImportMode.MERGE, SnapshotTransaction(store))
            store shouldBe listOf(Item("a", 9))
            report.sections.single() shouldBe SectionReport("items", added = 0, updated = 0, kept = 1, removed = 0, skipped = 0)
        }
        should("merging the same file twice reports zero added and zero updated the second time") {
            val store = mutableListOf<Item>()
            val files = backupFilesOf(info() to entriesJson(item("a", 1), item("b", 2)))
            val ready = readyOf(files, MergeableMemorySection(store = store))
            restoreBackup(ready, ImportMode.MERGE, SnapshotTransaction(store)).sections.single().added shouldBe 2
            val second = restoreBackup(ready, ImportMode.MERGE, SnapshotTransaction(store)).sections.single()
            second shouldBe SectionReport("items", added = 0, updated = 0, kept = 2, removed = 0, skipped = 0)
            store shouldBe listOf(Item("a", 1), Item("b", 2))
        }
        should("merge resolves duplicate keys inside one file before touching local data") {
            val store = mutableListOf(Item("a", 3))
            val files = backupFilesOf(info() to entriesJson(item("a", 7), item("a", 5), item("n", 1), item("n", 2)))
            val report = restoreBackup(readyOf(files, MergeableMemorySection(store = store)), ImportMode.MERGE, SnapshotTransaction(store))
            store shouldBe listOf(Item("a", 7), Item("n", 2))
            report.sections.single() shouldBe SectionReport("items", added = 1, updated = 1, kept = 0, removed = 0, skipped = 0)
        }
        should("merge on a replace-only backup throws") {
            val store = mutableListOf<Item>()
            val ready = readyOf(backupFilesOf(info() to entriesJson(item("a"))), MemorySection(store = store))
            shouldThrow<IllegalArgumentException> { restoreBackup(ready, ImportMode.MERGE, SnapshotTransaction(store)) }
            store shouldBe emptyList()
        }
        should("report the skipped count from the read phase") {
            val store = mutableListOf<Item>()
            val files = backupFilesOf(info() to entriesJson(item("a"), item("bad", -1), "7"))
            val ready = readyOf(files, MergeableMemorySection(store = store))
            restoreBackup(ready, ImportMode.REPLACE, SnapshotTransaction(store)).sections.single().skipped shouldBe 2
            restoreBackup(ready, ImportMode.MERGE, SnapshotTransaction(store)).sections.single().skipped shouldBe 2
        }
    },
)
