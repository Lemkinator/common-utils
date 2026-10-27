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
import de.lemke.commonutils.backup.core.BackupManifest
import de.lemke.commonutils.backup.core.BackupReadResult
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.commonutils.backup.core.ImportMode
import de.lemke.commonutils.backup.core.MergeableSection
import de.lemke.commonutils.backup.core.SectionInfo
import de.lemke.commonutils.backup.core.encodedOrder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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

/**
 * The tests every app runs over its real, store-backed [BackupSection], through the whole [BackupEngine] pipeline: export,
 * read, upgrade, validate, Replace and Merge. An app subclasses it under its JUnit 4 runner and supplies a section over an
 * empty store, the transaction of that store and the samples; it needs JUnit 4 and kotlinx-coroutines on its test classpath.
 */
public abstract class BackupSectionContract<E : Any> {
    /** A section over an empty store. */
    protected abstract fun section(): BackupSection<E>

    /** The transaction of the store behind [section]. */
    protected abstract fun transaction(): BackupTransaction

    /** At least two entries with distinct keys. */
    protected abstract fun sampleEntries(): List<E>

    /** A section file text `{"entries": [...]}` for each section version from `minReadableVersion` to `version`. */
    protected abstract fun fixtures(): Map<Int, String>

    /** One JSON object that the section must skip as a broken entry. */
    protected abstract fun brokenEntryJson(): String

    /** The clashes of the merge rule; empty for a section without merge rules. */
    protected open fun clashCases(): List<ClashCase<E>> = emptyList()

    private fun engineOf(section: BackupSection<E>) =
        BackupEngine(setOf(section), transaction(), BackupApp(CONTRACT_APP, 1), { CONTRACT_TIME })

    private suspend fun BackupEngine.ready(files: Map<String, ByteArray>): BackupReadResult.Ready {
        val result = read(files)
        assertTrue("The backup must be ready, was $result", result is BackupReadResult.Ready)
        return result as BackupReadResult.Ready
    }

    private suspend fun BackupSection<E>.sortedEntries(): List<E> = exportEntries().sortedWith(encodedOrder(serializer))

    @Test
    public fun exportThenReplaceRestoresTheSameEntries(): Unit =
        runBlocking {
            val section = section()
            val engine = engineOf(section)
            section.insert(sampleEntries())
            val files = engine.export()
            section.clear()

            engine.restore(engine.ready(files), ImportMode.REPLACE)

            assertEquals(sampleEntries().sortedWith(encodedOrder(section.serializer)), section.sortedEntries())
        }

    @Test
    public fun replaceTwiceGivesTheSameEntries(): Unit =
        runBlocking {
            val section = section()
            val engine = engineOf(section)
            section.insert(sampleEntries())
            val ready = engine.ready(engine.export())

            engine.restore(ready, ImportMode.REPLACE)
            val afterFirst = section.sortedEntries()
            engine.restore(ready, ImportMode.REPLACE)

            assertEquals(afterFirst, section.sortedEntries())
        }

    @Test
    public fun mergeTwiceChangesNothingTheSecondTime(): Unit =
        runBlocking {
            val section = section()
            val engine = engineOf(section)
            section.insert(sampleEntries())
            val ready = engine.ready(engine.export())
            if (ImportMode.MERGE in engine.modes) {
                section.clear()
                engine.restore(ready, ImportMode.MERGE)
                val afterFirst = section.sortedEntries()

                val second = engine.restore(ready, ImportMode.MERGE).sections.single()

                assertEquals(0, second.added)
                assertEquals(0, second.updated)
                assertEquals(afterFirst, section.sortedEntries())
            }
        }

    @Test
    public fun everyClashCaseKeepsTheExpectedEntry(): Unit =
        runBlocking {
            clashCases().forEach { case ->
                assertEquals(case.name, listOf(case.expected), merged(case.local, case.incoming))
            }
        }

    @Test
    public fun everySymmetricClashCaseKeepsTheSameEntryWithSidesSwapped(): Unit =
        runBlocking {
            clashCases().filter { it.symmetric }.forEach { case ->
                assertEquals(case.name, listOf(case.expected), merged(case.incoming, case.local))
            }
        }

    @Test
    public fun everyVersionFromMinReadableToCurrentHasAFixtureThatImports(): Unit =
        runBlocking {
            val probe = section()
            for (version in probe.minReadableVersion..probe.version) {
                val text = fixtures()[version]
                assertNotNull("The section needs a fixture for version $version", text)
                val section = section()
                val engine = engineOf(section)

                val ready = engine.ready(fileOf(section.id, version, text!!))

                val preview = ready.preview.sections.single()
                assertTrue("The fixture of version $version must hold an entry", preview.entries > 0)
                assertEquals("The fixture of version $version must hold no broken entry", 0, preview.skipped)
                engine.restore(ready, ImportMode.REPLACE)
                assertEquals(preview.entries, section.exportEntries().size)
            }
        }

    @Test
    public fun aBrokenEntryIsSkippedAndCounted(): Unit =
        runBlocking {
            val section = section()
            val engine = engineOf(section)
            section.insert(sampleEntries())
            val files = engine.export()
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

    private suspend fun merged(
        local: E,
        incoming: E,
    ): List<E> {
        val section = section()
        assertTrue("A clash case needs a mergeable section", section is MergeableSection<*, *>)
        val engine = engineOf(section)
        section.insert(listOf(incoming))
        val files = engine.export()
        section.clear()
        section.insert(listOf(local))

        engine.restore(engine.ready(files), ImportMode.MERGE)

        return section.exportEntries()
    }

    private fun fileOf(
        id: String,
        version: Int,
        sectionText: String,
    ): Map<String, ByteArray> {
        val manifest = BackupManifest(KIND, FORMAT, CONTRACT_APP, 1, CONTRACT_TIME, listOf(SectionInfo(id, version, 0)))
        return mapOf(
            "manifest.json" to Json.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray(),
            "sections/$id.json" to sectionText.encodeToByteArray(),
        )
    }

    private companion object {
        const val KIND = "backup"
        const val FORMAT = 1
        const val CONTRACT_APP = "contract"
        const val CONTRACT_TIME = "2026-10-27T14:30:00+01:00"
    }
}
