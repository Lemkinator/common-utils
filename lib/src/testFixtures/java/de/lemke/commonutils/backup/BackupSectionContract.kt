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
import de.lemke.commonutils.backup.core.BackupFileExtension
import de.lemke.commonutils.backup.core.BackupReadResult
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.commonutils.backup.core.ImportMode
import de.lemke.commonutils.backup.core.MergeableSection
import de.lemke.commonutils.backup.core.encodedOrder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A [BackupTransaction] that runs the block in place and counts its runs. */
public class DirectBackupTransaction : BackupTransaction {
    public var runs: Int = 0
        private set

    override suspend fun <R> run(block: suspend () -> R): R {
        runs++
        return block()
    }
}

/**
 * A merge clash: [local] meets [incoming] under the section's rule and [expected] stays.
 * [symmetric] is false for a rule that keeps phone-side fields by decision, so the swapped sides keep another entry.
 */
public data class ClashCase<E : Any>(
    val name: String,
    val local: E,
    val incoming: E,
    val expected: E,
    val symmetric: Boolean = true,
)

/** A section file text `{"entries": [...]}` of one version and the [entries] its import must produce. */
public data class SectionFixture<E : Any>(
    val text: String,
    val entries: List<E>,
)

/**
 * The tests every app runs over its real, store-backed [BackupSection], through the whole [BackupEngine] pipeline: export,
 * read, upgrade, validate, Replace and Merge. An app subclasses it under its JUnit 4 runner and supplies a section over an
 * empty store, the transaction of that store and the samples; it needs JUnit 4 and kotlinx-coroutines on its test classpath.
 * [section] may return sections over one shared store: a test clears it before each case that assumes it empty.
 */
public abstract class BackupSectionContract<E : Any> {
    /** A section over an empty store. */
    protected abstract fun section(): BackupSection<E>

    /** The transaction of the store behind [section]. */
    protected abstract fun transaction(): BackupTransaction

    /** At least two entries with distinct keys. */
    protected abstract fun sampleEntries(): List<E>

    /** A fixture for each section version from `minReadableVersion` to `version`. */
    protected abstract fun fixtures(): Map<Int, SectionFixture<E>>

    /** One JSON object that the section must skip as a broken entry. */
    protected abstract fun brokenEntryJson(): String

    /** The clashes of the merge rule; empty only for a section without merge rules. */
    protected open fun clashCases(): List<ClashCase<E>> = emptyList()

    private fun engineOf(section: BackupSection<E>) =
        BackupEngine(setOf(section), transaction(), BackupApp(CONTRACT_APP, 1, BackupFileExtension(CONTRACT_EXTENSION)), { CONTRACT_TIME })

    private suspend fun BackupEngine.ready(files: Map<String, ByteArray>): BackupReadResult.Ready {
        val result = read(files.toMutableMap())
        assertTrue("The backup must be ready, was $result", result is BackupReadResult.Ready)
        return result as BackupReadResult.Ready
    }

    private suspend fun <R> inTransaction(block: suspend () -> R): R = transaction().run(block)

    private suspend fun BackupSection<E>.sortedEntries(): List<E> = inTransaction { exportEntries() }.sortedWith(encodedOrder(serializer))

    @Test
    public fun exportThenReplaceRestoresTheSameEntries(): Unit =
        runBlocking {
            val section = section()
            val engine = engineOf(section)
            inTransaction { section.clear() }
            inTransaction { section.insert(sampleEntries()) }
            val files = engine.exportFiles()
            inTransaction { section.clear() }

            engine.restore(engine.ready(files), ImportMode.REPLACE)

            assertEquals(sampleEntries().sortedWith(encodedOrder(section.serializer)), section.sortedEntries())
        }

    @Test
    public fun replaceTwiceGivesTheSameEntries(): Unit =
        runBlocking {
            val section = section()
            val engine = engineOf(section)
            inTransaction { section.clear() }
            inTransaction { section.insert(sampleEntries()) }
            val ready = engine.ready(engine.exportFiles())

            engine.restore(ready, ImportMode.REPLACE)
            val afterFirst = section.sortedEntries()
            engine.restore(ready, ImportMode.REPLACE)

            assertEquals(afterFirst, section.sortedEntries())
        }

    @Test
    public fun replaceRemovesLocalOnlyEntries(): Unit =
        runBlocking {
            val section = section()
            val engine = engineOf(section)
            val (fileOnly, localOnly) = sampleEntries().take(2)
            inTransaction { section.clear() }
            inTransaction { section.insert(listOf(fileOnly)) }
            val files = engine.exportFiles()
            inTransaction { section.clear() }
            inTransaction { section.insert(listOf(localOnly)) }

            val report = engine.restore(engine.ready(files), ImportMode.REPLACE).sections.single()

            assertEquals(1, report.removed)
            assertEquals(listOf(fileOnly), section.sortedEntries())
        }

    @Test
    public fun mergeTwiceChangesNothingTheSecondTime(): Unit =
        runBlocking {
            val section = section()
            val engine = engineOf(section)
            inTransaction { section.clear() }
            inTransaction { section.insert(sampleEntries()) }
            val ready = engine.ready(engine.exportFiles())
            if (ImportMode.MERGE in engine.modes) {
                inTransaction { section.clear() }
                engine.restore(ready, ImportMode.MERGE)
                val afterFirst = section.sortedEntries()

                val second = engine.restore(ready, ImportMode.MERGE).sections.single()

                assertEquals(0, second.added)
                assertEquals(0, second.updated)
                assertEquals(afterFirst, section.sortedEntries())
            }
        }

    @Test
    public fun mergeKeepsLocalOnlyEntries(): Unit =
        runBlocking {
            val section = section()
            val engine = engineOf(section)
            if (ImportMode.MERGE in engine.modes) {
                val (fileOnly, localOnly) = sampleEntries().take(2)
                inTransaction { section.clear() }
                inTransaction { section.insert(listOf(fileOnly)) }
                val files = engine.exportFiles()
                inTransaction { section.clear() }
                inTransaction { section.insert(listOf(localOnly)) }

                val report = engine.restore(engine.ready(files), ImportMode.MERGE).sections.single()

                assertEquals(1, report.added)
                assertEquals(listOf(fileOnly, localOnly).sortedWith(encodedOrder(section.serializer)), section.sortedEntries())
            }
        }

    @Test
    public fun everyClashCaseKeepsTheExpectedEntry(): Unit =
        runBlocking {
            val section = section()
            section.requiredClashCases().forEach { case ->
                assertEquals(case.name, listOf(case.expected), section.merged(case.local, case.incoming))
            }
        }

    @Test
    public fun everySymmetricClashCaseKeepsTheSameEntryWithSidesSwapped(): Unit =
        runBlocking {
            val section = section()
            section.requiredClashCases().filter { it.symmetric }.forEach { case ->
                assertEquals(case.name, listOf(case.expected), section.merged(case.incoming, case.local))
            }
        }

    @Test
    public fun everyVersionFromMinReadableToCurrentHasAFixtureThatImports(): Unit =
        runBlocking {
            val section = section()
            val engine = engineOf(section)
            for (version in section.minReadableVersion..section.version) {
                val fixture = fixtures()[version]
                assertNotNull("The section needs a fixture for version $version", fixture)
                assertTrue("The fixture of version $version must expect an entry", fixture!!.entries.isNotEmpty())
                inTransaction { section.clear() }

                val ready = engine.ready(fileOf(section.id, version, fixture.text))

                assertEquals(
                    "The fixture of version $version must hold no broken entry",
                    0,
                    ready.preview.sections
                        .single()
                        .skipped,
                )
                engine.restore(ready, ImportMode.REPLACE)
                assertEquals(
                    "The fixture of version $version must import as the entries it expects",
                    fixture.entries.sortedWith(encodedOrder(section.serializer)),
                    section.sortedEntries(),
                )
            }
        }

    @Test
    public fun aBrokenEntryIsSkippedAndCounted(): Unit =
        runBlocking {
            val section = section()
            val engine = engineOf(section)
            inTransaction { section.clear() }
            inTransaction { section.insert(sampleEntries()) }
            val files = engine.exportFiles()
            val name = "sections/${section.id}.json"
            val broken = files.getValue(name).decodeToString().replaceFirst("\"entries\":[", "\"entries\":[${brokenEntryJson()},")

            val preview =
                engine
                    .ready(files + (name to broken.encodeToByteArray()))
                    .preview.sections
                    .single()

            assertEquals(1, preview.skipped)
            assertEquals(sampleEntries().size, preview.entries)
        }

    private fun BackupSection<E>.requiredClashCases(): List<ClashCase<E>> {
        val cases = clashCases()
        if (this is MergeableSection<*, *>) assertTrue("A MergeableSection needs clashCases", cases.isNotEmpty())
        return cases
    }

    private suspend fun BackupSection<E>.merged(
        local: E,
        incoming: E,
    ): List<E> {
        val section = this
        assertTrue("A clash case needs a mergeable section", section is MergeableSection<*, *>)
        val engine = engineOf(section)
        inTransaction { section.clear() }
        inTransaction { section.insert(listOf(incoming)) }
        val files = engine.exportFiles()
        inTransaction { section.clear() }
        inTransaction { section.insert(listOf(local)) }

        engine.restore(engine.ready(files), ImportMode.MERGE)

        return inTransaction { section.exportEntries() }
    }

    private fun fileOf(
        id: String,
        version: Int,
        sectionText: String,
    ): Map<String, ByteArray> {
        val manifest =
            buildJsonObject {
                put("kind", KIND)
                put("format", FORMAT)
                put("app", CONTRACT_APP)
                put("appVersionCode", 1)
                put("createdAt", CONTRACT_TIME)
                put(
                    "sections",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("id", id)
                                put("version", version)
                                put("entries", 0)
                            },
                        ),
                    ),
                )
            }
        return mapOf(
            "manifest.json" to manifest.toString().encodeToByteArray(),
            "sections/$id.json" to sectionText.encodeToByteArray(),
        )
    }
}

private const val KIND = "backup"
private const val FORMAT = 1
private const val CONTRACT_APP = "contract"
private const val CONTRACT_EXTENSION = ".contract"
private const val CONTRACT_TIME = "2026-10-27T14:30:00+01:00"
