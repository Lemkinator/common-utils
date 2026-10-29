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

import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.commonutils.backup.core.EntryCheck
import de.lemke.commonutils.backup.core.MergeableSection
import de.lemke.commonutils.backup.core.encodedOrder
import de.lemke.commonutils.backup.core.newerWins
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertThrows
import org.junit.Test

@Serializable
internal data class Event(val id: String, val stamp: Int, val note: String = "")

@Serializable
internal data class Note(val id: String, val title: String, val starred: Boolean)

private open class ListSection<E : Any>(
    override val id: String,
    override val serializer: KSerializer<E>,
    override val version: Int,
    val store: MutableList<E> = mutableListOf(),
    private val upgradeStep: (JsonObject) -> JsonObject = { it },
) : BackupSection<E> {
    override val minReadableVersion: Int = 1

    override fun upgrade(
        fromVersion: Int,
        entry: JsonObject,
    ): JsonObject = upgradeStep(entry)

    override fun validate(entry: E): EntryCheck = EntryCheck.Valid

    override suspend fun exportEntries(): List<E> = store.toList()

    override suspend fun clear() = store.clear()

    override suspend fun insert(entries: List<E>) {
        store += entries
    }
}

private open class MergeListSection<E : Any>(
    id: String,
    serializer: KSerializer<E>,
    version: Int,
    store: MutableList<E>,
    private val keyOf: (E) -> String,
    private val rule: (E, E) -> E,
    upgradeStep: (JsonObject) -> JsonObject = { it },
) : ListSection<E>(id, serializer, version, store, upgradeStep),
    MergeableSection<E, String> {
    override fun key(entry: E): String = keyOf(entry)

    override fun resolve(
        local: E,
        incoming: E,
    ): E = rule(local, incoming)

    override suspend fun localEntries(keys: Set<String>): Map<String, E> = store.filter { keyOf(it) in keys }.associateBy(keyOf)

    override suspend fun replace(
        local: E,
        merged: E,
    ) {
        store[store.indexOf(local)] = merged
    }
}

private val renameLegacyNote: (JsonObject) -> JsonObject = { entry ->
    JsonObject(entry.filterKeys { it != "legacyNote" } + listOfNotNull(entry["legacyNote"]?.let { "note" to it }))
}

private fun newerWinsEventSection(
    store: MutableList<Event>,
    upgradeStep: (JsonObject) -> JsonObject,
): BackupSection<Event> =
    MergeListSection(
        "events",
        Event.serializer(),
        version = 2,
        store,
        { it.id },
        newerWins({ it.stamp }, encodedOrder(Event.serializer())),
        upgradeStep,
    )

private val eventFixtures =
    mapOf(
        1 to SectionFixture("""{"entries":[{"id":"a","stamp":1,"legacyNote":"first"}]}""", listOf(Event("a", 1, "first"))),
        2 to SectionFixture("""{"entries":[{"id":"a","stamp":1,"note":"first"}]}""", listOf(Event("a", 1, "first"))),
    )

/** A section with merge rules, a newer-wins rule and two format versions. */
internal class NewerWinsSectionContractSelfTest : BackupSectionContract<Event>() {
    private val transaction = DirectBackupTransaction()
    private val store = mutableListOf<Event>()

    override fun section(): BackupSection<Event> = newerWinsEventSection(store, renameLegacyNote)

    override fun transaction(): BackupTransaction = transaction

    override fun sampleEntries(): List<Event> = listOf(Event("a", 1, "first"), Event("b", 2))

    override fun fixtures(): Map<Int, SectionFixture<Event>> = eventFixtures

    override fun brokenEntryJson(): String = """{"id":["not","a","string"]}"""

    override fun clashCases(): List<ClashCase<Event>> =
        listOf(
            ClashCase("equal stamps pick the entry greater by order", Event("a", 5, "x"), Event("a", 5, "y"), Event("a", 5, "y")),
            ClashCase("the newer stamp wins", Event("a", 1, "old"), Event("a", 2, "new"), Event("a", 2, "new")),
        )
}

/** A section whose rule keeps the title of the phone by decision, so one clash is not symmetric. */
internal class PhoneSideSectionContractSelfTest : BackupSectionContract<Note>() {
    private val transaction = DirectBackupTransaction()
    private val store = mutableListOf<Note>()

    override fun section(): BackupSection<Note> =
        MergeListSection("notes", Note.serializer(), version = 1, store, { it.id }, { local, incoming ->
            local.copy(starred = local.starred || incoming.starred)
        })

    override fun transaction(): BackupTransaction = transaction

    override fun sampleEntries(): List<Note> = listOf(Note("a", "first", false), Note("b", "second", true))

    override fun fixtures(): Map<Int, SectionFixture<Note>> = noteFixtures

    override fun brokenEntryJson(): String = """{"id":"a","title":"broken"}"""

    override fun clashCases(): List<ClashCase<Note>> =
        listOf(
            ClashCase("a star from the file wins", Note("a", "t", false), Note("a", "t", true), Note("a", "t", true)),
            ClashCase("a star on the phone stays", Note("a", "t", true), Note("a", "t", false), Note("a", "t", true)),
            ClashCase(
                "the title stays from the phone",
                Note("a", "phone", false),
                Note("a", "file", false),
                Note("a", "phone", false),
                symmetric = false,
            ),
        )
}

/** A section without merge rules: Replace only. */
internal class ReplaceOnlySectionContractSelfTest : BackupSectionContract<Note>() {
    private val transaction = DirectBackupTransaction()
    private val store = mutableListOf<Note>()

    override fun section(): BackupSection<Note> = ListSection("notes", Note.serializer(), version = 1, store)

    override fun transaction(): BackupTransaction = transaction

    override fun sampleEntries(): List<Note> = listOf(Note("a", "first", false), Note("b", "second", true))

    override fun fixtures(): Map<Int, SectionFixture<Note>> = noteFixtures

    override fun brokenEntryJson(): String = """{"id":"a"}"""
}

private val noteFixtures =
    mapOf(1 to SectionFixture("""{"entries":[{"id":"a","title":"first","starred":false}]}""", listOf(Note("a", "first", false))))

private fun <E : Any> contractOf(
    sectionOf: (MutableList<E>) -> BackupSection<E>,
    samples: List<E>,
    fixtures: Map<Int, SectionFixture<E>>,
    clashes: List<ClashCase<E>> = emptyList(),
): BackupSectionContract<E> =
    object : BackupSectionContract<E>() {
        private val store = mutableListOf<E>()
        private val transaction = DirectBackupTransaction()

        override fun section(): BackupSection<E> = sectionOf(store)

        override fun transaction(): BackupTransaction = transaction

        override fun sampleEntries(): List<E> = samples

        override fun fixtures(): Map<Int, SectionFixture<E>> = fixtures

        override fun brokenEntryJson(): String = """{"id":["not","a","string"]}"""

        override fun clashCases(): List<ClashCase<E>> = clashes
    }

private val noteSamples = listOf(Note("a", "first", false), Note("b", "second", true))

private fun localWinsNoteSection(store: MutableList<Note>): BackupSection<Note> =
    MergeListSection("notes", Note.serializer(), version = 1, store, { it.id }, { local, _ -> local })

/** A Replace that clears nothing and inserts by key, so entries only the phone holds survive. */
private class NoClearNoteSection(store: MutableList<Note>) : ListSection<Note>("notes", Note.serializer(), version = 1, store) {
    override suspend fun clear() = Unit

    override suspend fun insert(entries: List<Note>) {
        entries.forEach { entry ->
            store.removeAll { it.id == entry.id }
            store += entry
        }
    }
}

/** A Merge whose insert wipes the store first, so the entries only the phone holds vanish. */
private class WipingInsertNoteSection(store: MutableList<Note>) :
    MergeListSection<Note>("notes", Note.serializer(), version = 1, store, { it.id }, { local, _ -> local }) {
    override suspend fun insert(entries: List<Note>) {
        store.clear()
        store += entries
    }
}

/** A Merge that finds no local counterpart, so it inserts every incoming entry again. */
private class BlindLocalEntriesNoteSection(store: MutableList<Note>) :
    MergeListSection<Note>("notes", Note.serializer(), version = 1, store, { it.id }, { local, _ -> local }) {
    override suspend fun localEntries(keys: Set<String>): Map<String, Note> = emptyMap()
}

class BackupSectionContractNegativeSelfTest {
    @Test
    fun `a local-wins rule marked symmetric fails the swapped-sides check`() {
        val contract =
            contractOf(
                ::localWinsNoteSection,
                noteSamples,
                noteFixtures,
                listOf(ClashCase("local wins", Note("a", "phone", false), Note("a", "file", false), Note("a", "phone", false))),
            )

        contract.everyClashCaseKeepsTheExpectedEntry()
        assertThrows(AssertionError::class.java) { contract.everySymmetricClashCaseKeepsTheSameEntryWithSidesSwapped() }
    }

    @Test
    fun `a mergeable section without clash cases fails both clash checks`() {
        val contract = contractOf(::localWinsNoteSection, noteSamples, noteFixtures)

        assertThrows(AssertionError::class.java) { contract.everyClashCaseKeepsTheExpectedEntry() }
        assertThrows(AssertionError::class.java) { contract.everySymmetricClashCaseKeepsTheSameEntryWithSidesSwapped() }
    }

    @Test
    fun `a fixture that does not import fails the fixture check`() {
        val broken = mapOf(1 to SectionFixture("""{"entries":[{"id":"a"}]}""", listOf(Note("a", "first", false))))
        val contract = contractOf(::localWinsNoteSection, noteSamples, broken)

        assertThrows(AssertionError::class.java) { contract.everyVersionFromMinReadableToCurrentHasAFixtureThatImports() }
    }

    @Test
    fun `an upgrade that maps an entry to defaults fails the fixture check`() {
        val keepsData =
            contractOf({ store -> newerWinsEventSection(store, renameLegacyNote) }, listOf(Event("a", 1), Event("b", 2)), eventFixtures)
        val dropsData = contractOf({ store -> newerWinsEventSection(store) { it } }, listOf(Event("a", 1), Event("b", 2)), eventFixtures)

        keepsData.everyVersionFromMinReadableToCurrentHasAFixtureThatImports()
        assertThrows(AssertionError::class.java) { dropsData.everyVersionFromMinReadableToCurrentHasAFixtureThatImports() }
    }

    @Test
    fun `a Replace that keeps local-only entries fails the removal check`() {
        val contract = contractOf(::NoClearNoteSection, noteSamples, noteFixtures)

        assertThrows(AssertionError::class.java) { contract.replaceRemovesLocalOnlyEntries() }
    }

    @Test
    fun `a Merge that wipes the store on insert fails the local-only check`() {
        val contract = contractOf(::WipingInsertNoteSection, noteSamples, noteFixtures)

        assertThrows(AssertionError::class.java) { contract.mergeKeepsLocalOnlyEntries() }
    }

    @Test
    fun `a Merge that finds no local entries fails the second-merge check`() {
        val contract = contractOf(::BlindLocalEntriesNoteSection, noteSamples, noteFixtures)

        assertThrows(AssertionError::class.java) { contract.mergeTwiceChangesNothingTheSecondTime() }
    }

    @Test
    fun `a broken entry that decodes fine fails the skip check`() {
        val contract =
            object : BackupSectionContract<Note>() {
                private val store = mutableListOf<Note>()
                private val transaction = DirectBackupTransaction()

                override fun section(): BackupSection<Note> = localWinsNoteSection(store)

                override fun transaction(): BackupTransaction = transaction

                override fun sampleEntries(): List<Note> = noteSamples

                override fun fixtures(): Map<Int, SectionFixture<Note>> = noteFixtures

                override fun brokenEntryJson(): String = """{"id":"z","title":"fine","starred":false}"""
            }

        assertThrows(AssertionError::class.java) { contract.aBrokenEntryIsSkippedAndCounted() }
    }
}
