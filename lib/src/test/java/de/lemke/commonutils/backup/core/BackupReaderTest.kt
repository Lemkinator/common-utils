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
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

private fun read(
    files: Map<String, ByteArray>,
    vararg sections: BackupSection<*>,
): BackupReadResult = readBackup(files, sections.toSet(), app, null)

private fun refusal(result: BackupReadResult): BackupRefusal = result.shouldBeInstanceOf<BackupReadResult.Refused>().reason

private fun ready(result: BackupReadResult): BackupReadResult.Ready = result.shouldBeInstanceOf<BackupReadResult.Ready>()

private val items = SectionInfo("items", 1, 1)

// An entry with `count` values: its braces, its array and the commas of the array.
private fun entryWithValues(count: Int): String = """{"key":[${List(count - 1) { "0" }.joinToString(",")}]}"""

private fun Map<String, ByteArray>.manifestWithNestedKey(levels: Int): ByteArray =
    getValue(
        MANIFEST_ENTRY,
    ).decodeToString().removeSuffix("}").plus(""","extra":${"[".repeat(levels)}${"]".repeat(levels)}}""").encodeToByteArray()

class BackupReaderTest : ShouldSpec(
    {
        should("refuse a file without manifest") {
            refusal(read(emptyMap(), MemorySection())) shouldBe BackupRefusal.NotABackup
        }
        should("refuse a manifest that is no JSON") {
            refusal(read(mapOf(MANIFEST_ENTRY to "nope".encodeToByteArray()), MemorySection())) shouldBe BackupRefusal.NotABackup
        }
        should("refuse a manifest with kind pack") {
            val files = backupFilesOf(items to entriesJson(), kind = "pack")
            refusal(read(files, MemorySection())) shouldBe BackupRefusal.NotABackup
        }
        should("refuse another app id") {
            val files = backupFilesOf(items to entriesJson(), appId = "sudoku")
            refusal(read(files, MemorySection())) shouldBe BackupRefusal.OtherApp("sudoku")
        }
        should("refuse format 2") {
            val files = backupFilesOf(items to entriesJson(), format = 2)
            refusal(read(files, MemorySection())) shouldBe BackupRefusal.NewerFormat(2)
        }
        should("refuse a manifest that lists a section twice") {
            val files = backupFilesOf(items to entriesJson(), items to entriesJson())
            refusal(read(files, MemorySection())) shouldBe BackupRefusal.NotABackup
        }
        should("refuse a newer section before decoding any entry") {
            val files = backupFilesOf(SectionInfo("items", 2, 1) to entriesJson("""{"key":1}"""))
            refusal(read(files, MemorySection())) shouldBe BackupRefusal.NewerSection("items", 2)
        }
        should("refuse a section below minReadableVersion") {
            val files = backupFilesOf(SectionInfo("items", 1, 1) to entriesJson(item("a")))
            refusal(read(files, MemorySection(version = 3, minReadableVersion = 2))) shouldBe BackupRefusal.TooOldSection("items", 1)
        }
        should("refuse a section file that is a bare array") {
            val files = backupFilesOf(items to "[${item("a")}]")
            refusal(read(files, MemorySection())) shouldBe BackupRefusal.NotABackup
        }
        should("refuse a listed section without its zip entry") {
            val files = backupFilesOf(items to entriesJson(item("a"))) - sectionEntryName("items")
            refusal(read(files, MemorySection())) shouldBe BackupRefusal.NotABackup
        }
        should("skip an unknown section and restore the known one") {
            val files = backupFilesOf(items to entriesJson(item("a", 1)), SectionInfo("extra", 5, 1) to "garbage")
            val ready = ready(read(files, MemorySection()))
            ready.preview.skippedSections shouldContainExactly listOf("extra")
            ready.preview.sections shouldContainExactly listOf(SectionPreview("items", 1, 0))
            ready.sections.single().entries shouldBe listOf(Item("a", 1))
        }
        should("refuse a newer known section even when an unknown section is present") {
            val files = backupFilesOf(SectionInfo("items", 2, 1) to entriesJson(), SectionInfo("extra", 1, 1) to entriesJson())
            refusal(read(files, MemorySection())) shouldBe BackupRefusal.NewerSection("items", 2)
        }
        should("leave a bound section out when the manifest lacks it") {
            val files = backupFilesOf(items to entriesJson(item("a")))
            val ready = ready(read(files, MemorySection(), MemorySection(id = "other")))
            ready.preview.sections.map { it.id } shouldContainExactly listOf("items")
            ready.sections.map { it.section.id } shouldContainExactly listOf("items")
        }
        should("ignore an unlisted sections entry") {
            val files = backupFilesOf(items to entriesJson(item("a"))) + (sectionEntryName("stray") to "garbage".encodeToByteArray())
            ready(read(files, MemorySection())).preview.sections.map { it.id } shouldContainExactly listOf("items")
        }
        should("ignore an unknown key in the section file and an unknown field in an entry") {
            val text = """{"deleted":[],"entries":[{"key":"a","value":1,"extra":true}]}"""
            val ready = ready(read(backupFilesOf(items to text), MemorySection()))
            ready.sections.single().entries shouldBe listOf(Item("a", 1))
        }
        should("upgrade a v1 entry through every step to the current version") {
            val steps = mutableListOf<Int>()
            val section =
                MemorySection(
                    version = 3,
                    upgradeStep = { from, entry ->
                        steps += from
                        JsonObject(entry + ("value" to JsonPrimitive((entry["value"]?.jsonPrimitive?.int ?: 0) + 10)))
                    },
                )
            val files = backupFilesOf(SectionInfo("items", 1, 1) to entriesJson("""{"key":"a"}"""))
            ready(read(files, section)).sections.single().entries shouldBe listOf(Item("a", 20))
            steps shouldContainExactly listOf(1, 2)
        }
        should("skip and count an entry that is no object, fails decode, upgrade or validate") {
            val section =
                MemorySection(
                    version = 2,
                    upgradeStep = { _, entry ->
                        require(entry["key"]?.jsonPrimitive?.content != "bad-upgrade") { "cannot map" }
                        entry
                    },
                )
            val text =
                entriesJson(
                    item("good1"),
                    "5",
                    """{"key":["not","a","string"]}""",
                    item("bad-upgrade"),
                    item("negative", -1),
                    item("good2"),
                )
            val ready = ready(read(backupFilesOf(SectionInfo("items", 1, 6) to text), section))
            ready.preview.sections shouldContainExactly listOf(SectionPreview("items", 2, 4))
        }
        should("skip and count an entry whose upgrade throws a non-argument exception") {
            val section =
                MemorySection(
                    version = 2,
                    upgradeStep = { _, entry ->
                        if (entry["key"]?.jsonPrimitive?.content == "bad-date") throw DateTimeParseException("not a date", "x", 0)
                        entry
                    },
                )
            val text = entriesJson(item("good"), item("bad-date"))
            val ready = ready(read(backupFilesOf(SectionInfo("items", 1, 2) to text), section))
            ready.preview.sections shouldContainExactly listOf(SectionPreview("items", 1, 1))
        }
        should("let a CancellationException from an upgrade step propagate") {
            val section = MemorySection(version = 2, upgradeStep = { _, _ -> throw CancellationException("canceled") })
            shouldThrow<CancellationException> {
                read(backupFilesOf(SectionInfo("items", 1, 1) to entriesJson(item("a"))), section)
            }
        }
        should("refuse a file whose sections are all unknown") {
            val files = backupFilesOf(SectionInfo("extra", 1, 1) to entriesJson(item("a")))
            refusal(read(files, MemorySection())) shouldBe BackupRefusal.NoKnownSection
        }
        should("read a section with the most entries and refuse one entry more") {
            val atLimit = entriesJson(*Array(MAX_SECTION_ENTRIES) { """{"key":"k"}""" })
            val overLimit = entriesJson(*Array(MAX_SECTION_ENTRIES + 1) { """{"key":"k"}""" })

            ready(read(backupFilesOf(SectionInfo("items", 1, MAX_SECTION_ENTRIES) to atLimit), MemorySection()))
                .preview.sections
                .single()
                .entries shouldBe MAX_SECTION_ENTRIES
            refusal(read(backupFilesOf(SectionInfo("items", 1, MAX_SECTION_ENTRIES + 1) to overLimit), MemorySection())) shouldBe
                BackupRefusal.TooLarge
        }
        should("read a section file of the most entries with twenty fields each") {
            val fields = (1..19).joinToString("") { ""","f$it":$it""" }
            val entry = """{"key":"k"$fields}"""
            val text = entriesJson(*Array(MAX_SECTION_ENTRIES) { entry })

            ready(read(backupFilesOf(SectionInfo("items", 1, MAX_SECTION_ENTRIES) to text), MemorySection()))
                .preview.sections shouldContainExactly listOf(SectionPreview("items", MAX_SECTION_ENTRIES, 0))
        }
        should("read a section file that nests as deep as the limit and skip its entry") {
            val levels = MAX_NESTING_DEPTH - 3
            val nested = "[".repeat(levels) + "]".repeat(levels)
            val ready = ready(read(backupFilesOf(items to entriesJson("""{"key":$nested}""")), MemorySection()))
            ready.preview.sections shouldContainExactly listOf(SectionPreview("items", 0, 1))
        }
        should("refuse a section file that nests deeper than the limit") {
            val levels = MAX_NESTING_DEPTH - 2
            val nested = "[".repeat(levels) + "]".repeat(levels)
            refusal(read(backupFilesOf(items to entriesJson("""{"key":$nested}""")), MemorySection())) shouldBe BackupRefusal.TooLarge
        }
        should("read an entry with the most values and refuse one with a value more") {
            val atLimit = ready(read(backupFilesOf(items to entriesJson(entryWithValues(MAX_ENTRY_VALUES))), MemorySection()))
            atLimit.preview.sections shouldContainExactly listOf(SectionPreview("items", 0, 1))
            val overLimit = backupFilesOf(items to entriesJson(entryWithValues(MAX_ENTRY_VALUES + 1)))
            refusal(read(overLimit, MemorySection())) shouldBe BackupRefusal.TooLarge
        }
        should("count the values of each entry on its own") {
            val entries = Array(3) { entryWithValues(MAX_ENTRY_VALUES) }
            val ready = ready(read(backupFilesOf(SectionInfo("items", 1, 3) to entriesJson(*entries)), MemorySection()))
            ready.preview.sections shouldContainExactly listOf(SectionPreview("items", 0, 3))
        }
        should("read a manifest that nests as deep as the limit and refuse one deeper") {
            val files = backupFilesOf(items to entriesJson(item("a")))
            ready(read(files + (MANIFEST_ENTRY to files.manifestWithNestedKey(MAX_NESTING_DEPTH - 1)), MemorySection()))
            refusal(read(files + (MANIFEST_ENTRY to files.manifestWithNestedKey(MAX_NESTING_DEPTH)), MemorySection())) shouldBe
                BackupRefusal.NotABackup
        }
        should("count brackets, commas and escaped quotes inside a string as text") {
            val text = "\\\"" + "[{,".repeat(MAX_NESTING_DEPTH) + "\\\\"
            val ready = ready(read(backupFilesOf(items to entriesJson("""{"key":"$text"}""")), MemorySection()))
            ready.preview.sections shouldContainExactly listOf(SectionPreview("items", 1, 0))
        }
        should("offer no mode without a section") {
            importModes(emptyList()) shouldBe emptySet()
        }
        should("offer MERGE and REPLACE only when every present section is mergeable") {
            val files = backupFilesOf(items to entriesJson())
            ready(read(files, MergeableMemorySection())).preview.modes shouldBe setOf(ImportMode.MERGE, ImportMode.REPLACE)
        }
        should("offer REPLACE only otherwise") {
            val files = backupFilesOf(items to entriesJson())
            ready(read(files, MemorySection())).preview.modes shouldBe setOf(ImportMode.REPLACE)
        }
        should("carry the manifest date and app version into the preview") {
            val preview = ready(read(backupFilesOf(items to entriesJson()), MemorySection())).preview
            preview.createdAt shouldBe "2026-10-27T14:30:00+01:00"
            preview.appVersionCode shouldBe 7
        }
    },
)
