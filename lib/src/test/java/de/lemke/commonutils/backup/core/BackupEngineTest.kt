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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private class CountingTransaction : BackupTransaction {
    var runs = 0
    var active = false

    override suspend fun <R> run(block: suspend () -> R): R {
        runs++
        active = true
        try {
            return block()
        } finally {
            active = false
        }
    }
}

private class ObservingSection(id: String, private val transaction: CountingTransaction, store: MutableList<Item>) : MemorySection(
    id = id,
    store = store,
) {
    val exportedInsideTransaction = mutableListOf<Boolean>()

    override suspend fun exportEntries(): List<Item> = super.exportEntries().also { exportedInsideTransaction += transaction.active }
}

private const val NOW = "2026-10-27T14:30:00+01:00"

class BackupEngineTest : ShouldSpec(
    {
        should("export a manifest with kind backup, format 1, the app id and the clock text") {
            val store = mutableListOf(Item("a", 1))
            val engine = BackupEngine(setOf(MemorySection(store = store)), CountingTransaction(), app, { NOW })
            val manifest = backupJson.parseToJsonElement(engine.export().getValue(MANIFEST_ENTRY).decodeToString()).jsonObject
            manifest.getValue("kind").jsonPrimitive.content shouldBe "backup"
            manifest.getValue("format").jsonPrimitive.content shouldBe "1"
            manifest.getValue("app").jsonPrimitive.content shouldBe "oneurl"
            manifest.getValue("appVersionCode").jsonPrimitive.content shouldBe "100"
            manifest.getValue("createdAt").jsonPrimitive.content shouldBe NOW
            manifest.getValue("sections").toString() shouldBe """[{"id":"items","version":1,"entries":1}]"""
        }
        should("export each section as an object with an entries array") {
            val engine =
                BackupEngine(setOf(MemorySection(store = mutableListOf(Item("a", 1), Item("b", 2)))), CountingTransaction(), app, { NOW })
            engine.export().getValue("sections/items.json").decodeToString() shouldBe
                """{"entries":[{"key":"a","value":1},{"key":"b","value":2}]}"""
        }
        should("export then read then replace into empty sections restores the same entries") {
            val source = mutableListOf(Item("a", 1), Item("b", 2))
            val exporter = BackupEngine(setOf(MemorySection(store = source)), CountingTransaction(), app, { NOW })
            val target = mutableListOf<Item>()
            val importer = BackupEngine(setOf(MemorySection(store = target)), CountingTransaction(), app, { NOW })
            val ready = importer.read(exporter.export().toMutableMap()).shouldBeInstanceOf<BackupReadResult.Ready>()
            importer
                .restore(ready, ImportMode.REPLACE)
                .sections
                .single()
                .added shouldBe 2
            target shouldBe source
        }
        should("export reads all sections inside one transaction") {
            val transaction = CountingTransaction()
            val first = ObservingSection("first", transaction, mutableListOf())
            val second = ObservingSection("second", transaction, mutableListOf())
            BackupEngine(setOf(first, second), transaction, app, { NOW }).export()
            transaction.runs shouldBe 1
            first.exportedInsideTransaction shouldContainExactly listOf(true)
            second.exportedInsideTransaction shouldContainExactly listOf(true)
        }
        should("list the sections in the order the engine got them") {
            val engine = BackupEngine(setOf(MemorySection(id = "b"), MemorySection(id = "a")), CountingTransaction(), app, { NOW })
            val manifest =
                backupJson.decodeFromString(
                    BackupManifest.serializer(),
                    engine.export().getValue(MANIFEST_ENTRY).decodeToString(),
                )
            manifest.sections.map { it.id } shouldContainExactly listOf("b", "a")
        }
        should("reject two sections with the same id") {
            shouldThrow<IllegalArgumentException> {
                BackupEngine(setOf(MemorySection(), MemorySection()), CountingTransaction(), app, { NOW })
            }
        }
        should("stop reading the entries of a section when the calling coroutine is canceled") {
            val job = Job()
            val validated = mutableListOf<String>()
            val section =
                object : MemorySection(store = mutableListOf(Item("a", 1), Item("b", 2))) {
                    override fun validate(entry: Item): EntryCheck {
                        validated += entry.key
                        job.cancel()
                        return super.validate(entry)
                    }
                }
            val engine = BackupEngine(setOf(section), CountingTransaction(), app, { NOW })
            val files = engine.export()

            shouldThrow<CancellationException> { withContext(job) { engine.read(files.toMutableMap()) } }
            validated shouldContainExactly listOf("a")
        }
        should("offer MERGE only when every bound section is mergeable") {
            BackupEngine(setOf(MergeableMemorySection()), CountingTransaction(), app, { NOW }).modes shouldBe
                setOf(ImportMode.MERGE, ImportMode.REPLACE)
            BackupEngine(
                setOf(MergeableMemorySection(id = "a"), MemorySection(id = "b")),
                CountingTransaction(),
                app,
                { NOW },
            ).modes shouldBe
                setOf(ImportMode.REPLACE)
        }
    },
)
