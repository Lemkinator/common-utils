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

@Serializable
internal data class Event(val id: String, val stamp: Int, val note: String = "")

@Serializable
internal data class Note(val id: String, val title: String, val starred: Boolean)

private open class ListSection<E : Any>(
    override val id: String,
    override val serializer: KSerializer<E>,
    override val version: Int,
    val store: MutableList<E> = mutableListOf(),
) : BackupSection<E> {
    override val minReadableVersion: Int = 1

    override fun upgrade(
        fromVersion: Int,
        entry: JsonObject,
    ): JsonObject = entry

    override fun validate(entry: E): EntryCheck = EntryCheck.Valid

    override suspend fun exportEntries(): List<E> = store.toList()

    override suspend fun clear() = store.clear()

    override suspend fun insert(entries: List<E>) {
        store += entries
    }
}

private class MergeListSection<E : Any>(
    id: String,
    serializer: KSerializer<E>,
    version: Int,
    private val keyOf: (E) -> String,
    private val rule: (E, E) -> E,
) : ListSection<E>(id, serializer, version),
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

/** A section with merge rules, a newer-wins rule and two format versions. */
internal class NewerWinsSectionContractSelfTest : BackupSectionContract<Event>() {
    private val transaction = DirectBackupTransaction()

    override fun section(): BackupSection<Event> =
        MergeListSection("events", Event.serializer(), version = 2, { it.id }, newerWins({ it.stamp }, encodedOrder(Event.serializer())))

    override fun transaction(): BackupTransaction = transaction

    override fun sampleEntries(): List<Event> = listOf(Event("a", 1, "first"), Event("b", 2))

    override fun fixtures(): Map<Int, String> =
        mapOf(
            1 to """{"entries":[{"id":"a","stamp":1}]}""",
            2 to """{"entries":[{"id":"a","stamp":1,"note":"first"}]}""",
        )

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

    override fun section(): BackupSection<Note> =
        MergeListSection("notes", Note.serializer(), version = 1, { it.id }, { local, incoming ->
            local.copy(
                starred =
                    local.starred || incoming.starred,
            )
        })

    override fun transaction(): BackupTransaction = transaction

    override fun sampleEntries(): List<Note> = listOf(Note("a", "first", false), Note("b", "second", true))

    override fun fixtures(): Map<Int, String> = mapOf(1 to """{"entries":[{"id":"a","title":"first","starred":false}]}""")

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

    override fun section(): BackupSection<Note> = ListSection("notes", Note.serializer(), version = 1)

    override fun transaction(): BackupTransaction = transaction

    override fun sampleEntries(): List<Note> = listOf(Note("a", "first", false), Note("b", "second", true))

    override fun fixtures(): Map<Int, String> = mapOf(1 to """{"entries":[{"id":"a","title":"first","starred":false}]}""")

    override fun brokenEntryJson(): String = """{"id":"a"}"""
}
