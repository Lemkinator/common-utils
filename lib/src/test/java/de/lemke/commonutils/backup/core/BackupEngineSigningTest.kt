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

import de.lemke.commonutils.backup.exportFiles
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val SIGNED_BULK_ITEMS = 15
private const val SIGNED_BULK_PAD = 1_000_000
private const val SIGNED_PAD = 500_000

private class FoldSigner(
    override val keyId: String = "test",
    private val onPart: (ByteArray) -> Unit = {},
) : BackupSigner {
    override fun mac(parts: Sequence<ByteArray>): ByteArray {
        val out = ByteArray(32)
        var index = 0
        parts.forEach { part ->
            onPart(part)
            part.forEach { byte ->
                out[index % 32] = (out[index % 32] + byte * (index % 251 + 1)).toByte()
                index++
            }
        }
        return out
    }
}

private fun engine(
    store: MutableList<Item>,
    signer: BackupSigner?,
    extra: BackupSection<*>? = null,
) = BackupEngine(setOfNotNull(MemorySection(store = store), extra), DirectTransaction, app, { "2026-10-27T14:30:00+01:00" }, signer)

private object DirectTransaction : BackupTransaction {
    override suspend fun <R> run(block: suspend () -> R): R = block()
}

private suspend fun signedExport(signer: BackupSigner = FoldSigner()): Map<String, ByteArray> =
    engine(mutableListOf(Item("a", 1)), signer).exportFiles()

// name, 0x00, length as 8 bytes big-endian, bytes; manifest.json first, then the section entries by name
private fun handBuiltPayload(files: Map<String, ByteArray>): ByteArray {
    fun framed(name: String): ByteArray {
        val bytes = files.getValue(name)
        require(bytes.size < 256) { "The test export must stay below 256 bytes per entry" }
        return name.encodeToByteArray() + byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, bytes.size.toByte()) + bytes
    }
    return framed("manifest.json") + framed("sections/items.json")
}

private suspend fun refusalOf(
    files: Map<String, ByteArray>,
    signer: BackupSigner? = FoldSigner(),
): BackupRefusal = engine(mutableListOf(), signer).read(files.toMutableMap()).shouldBeInstanceOf<BackupReadResult.Refused>().reason

class BackupEngineSigningTest : ShouldSpec(
    {
        should("export with a signer writes signature.json with alg, keyId and a hex mac") {
            val files = signedExport()
            val signature = backupJson.parseToJsonElement(files.getValue(SIGNATURE_ENTRY).decodeToString()).jsonObject
            signature.getValue("alg").jsonPrimitive.content shouldBe "HMAC-SHA256"
            signature.getValue("keyId").jsonPrimitive.content shouldBe "test"
            signature.getValue("mac").jsonPrimitive.content shouldBe FoldSigner().mac(handBuiltPayload(files)).toHex()
        }
        should("hand the signer the name, separator, length and bytes of each entry as separate parts") {
            val sizes = mutableListOf<Int>()
            val files = engine(mutableListOf(Item("a", 1)), FoldSigner(onPart = { sizes += it.size })).exportFiles()

            sizes shouldBe
                listOf(
                    13,
                    1,
                    8,
                    files.getValue(MANIFEST_ENTRY).size,
                    19,
                    1,
                    8,
                    files.getValue("sections/items.json").size,
                )
        }
        should("stop signing an export when the calling coroutine is canceled") {
            val job = Job()
            var parts = 0
            val signer =
                FoldSigner(onPart = {
                    parts++
                    job.cancel()
                })

            shouldThrow<CancellationException> { withContext(job) { engine(mutableListOf(Item("a", 1)), signer).exportFiles() } }
            parts shouldBe 1
        }
        should("stop checking the signature of a read when the calling coroutine is canceled") {
            val files = signedExport()
            val job = Job()
            var parts = 0
            val signer =
                FoldSigner(onPart = {
                    parts++
                    job.cancel()
                })

            shouldThrow<CancellationException> { withContext(job) { engine(mutableListOf(), signer).read(files.toMutableMap()) } }
            parts shouldBe 1
        }
        should("count the signature toward the total an import accepts") {
            fun engineOf(
                tunerItems: Int,
                extra: Int,
            ): BackupEngine {
                val tuner = MutableList(tunerItems) { Item("k$it".padEnd(SIGNED_PAD, 'x')) } + Item("t".padEnd(SIGNED_PAD + extra, 'x'))
                val sections =
                    listOf(
                        MemorySection(id = "a", store = MutableList(SIGNED_BULK_ITEMS) { Item("k$it".padEnd(SIGNED_BULK_PAD, 'x')) }),
                        MemorySection(id = "b", store = MutableList(SIGNED_BULK_ITEMS) { Item("k$it".padEnd(SIGNED_BULK_PAD, 'x')) }),
                        MemorySection(id = "c", store = tuner.toMutableList()),
                    )
                return BackupEngine(sections.toSet(), DirectTransaction, app, { "2026-10-27T14:30:00+01:00" }, FoldSigner())
            }

            suspend fun totalOf(
                tunerItems: Int,
                extra: Int,
            ): Long = engineOf(tunerItems, extra).exportFiles().values.sumOf { it.size.toLong() }
            val base = totalOf(0, 0)
            val itemSize = totalOf(1, 0) - base
            val missing = MAX_TOTAL_BYTES - base

            totalOf((missing / itemSize).toInt(), (missing % itemSize).toInt()) shouldBe MAX_TOTAL_BYTES
            engineOf((missing / itemSize).toInt(), (missing % itemSize).toInt() + 1).export() shouldBe BackupExport.TooLarge
        }
        should("export without a signer writes no signature entry") {
            (SIGNATURE_ENTRY in engine(mutableListOf(), null).exportFiles()) shouldBe false
        }
        should("a signing engine reads its own export") {
            engine(mutableListOf(), FoldSigner()).read(signedExport().toMutableMap()).shouldBeInstanceOf<BackupReadResult.Ready>()
        }
        should("refuse an unsigned file with Tampered") {
            refusalOf(signedExport() - SIGNATURE_ENTRY) shouldBe BackupRefusal.Tampered
        }
        should("refuse an unparsable signature with Tampered") {
            refusalOf(signedExport() + (SIGNATURE_ENTRY to "nope".encodeToByteArray())) shouldBe BackupRefusal.Tampered
        }
        should("refuse a signature without its mac") {
            refusalOf(signedExport() + (SIGNATURE_ENTRY to """{"alg":"HMAC-SHA256","keyId":"test"}""".encodeToByteArray())) shouldBe
                BackupRefusal.Tampered
        }
        should("refuse a file whose section bytes changed") {
            val files = signedExport()
            val changed =
                files
                    .getValue("sections/items.json")
                    .decodeToString()
                    .replace("\"value\":1", "\"value\":9")
                    .encodeToByteArray()
            refusalOf(files + ("sections/items.json" to changed)) shouldBe BackupRefusal.Tampered
        }
        should("refuse a file whose manifest changed") {
            val files = signedExport()
            val changed =
                files
                    .getValue(MANIFEST_ENTRY)
                    .decodeToString()
                    .replace("100", "101")
                    .encodeToByteArray()
            refusalOf(files + (MANIFEST_ENTRY to changed)) shouldBe BackupRefusal.Tampered
        }
        should("refuse a file with an added sections entry") {
            refusalOf(signedExport() + ("sections/extra.json" to entriesJson().encodeToByteArray())) shouldBe BackupRefusal.Tampered
        }
        should("read a signature that nests as deep as the limit and refuse one deeper") {
            val files = signedExport()

            fun nested(levels: Int) =
                files
                    .getValue(
                        SIGNATURE_ENTRY,
                    ).decodeToString()
                    .removeSuffix("}")
                    .plus(""","extra":${"[".repeat(levels)}${"]".repeat(levels)}}""")
            engine(mutableListOf(), FoldSigner())
                .read((files + (SIGNATURE_ENTRY to nested(MAX_NESTING_DEPTH - 1).encodeToByteArray())).toMutableMap())
                .shouldBeInstanceOf<BackupReadResult.Ready>()
            refusalOf(files + (SIGNATURE_ENTRY to nested(MAX_NESTING_DEPTH).encodeToByteArray())) shouldBe BackupRefusal.Tampered
        }
        should("refuse another keyId") {
            refusalOf(signedExport(FoldSigner("other"))) shouldBe BackupRefusal.Tampered
        }
        should("refuse another alg") {
            val files = signedExport()
            val signature = backupJson.parseToJsonElement(files.getValue(SIGNATURE_ENTRY).decodeToString()).jsonObject
            val changed = JsonObject(signature + ("alg" to JsonPrimitive("HMAC-SHA512"))).toString().encodeToByteArray()
            refusalOf(files + (SIGNATURE_ENTRY to changed)) shouldBe BackupRefusal.Tampered
        }
        should("refuse Tampered before any section decode") {
            val files = signedExport()
            val newer =
                files
                    .getValue(MANIFEST_ENTRY)
                    .decodeToString()
                    .replace("\"version\":1", "\"version\":2")
                    .encodeToByteArray()
            refusalOf(files + (MANIFEST_ENTRY to newer)) shouldBe BackupRefusal.Tampered
            refusalOf(files + (MANIFEST_ENTRY to newer), signer = null) shouldBe BackupRefusal.NewerSection("items", 2)
        }
        should("report OtherApp before the signature check") {
            val files = signedExport() - SIGNATURE_ENTRY
            val other =
                files
                    .getValue(MANIFEST_ENTRY)
                    .decodeToString()
                    .replace("oneurl", "sudoku")
                    .encodeToByteArray()
            refusalOf(files + (MANIFEST_ENTRY to other)) shouldBe BackupRefusal.OtherApp("sudoku")
        }
        should("an engine without a signer reads a signed file") {
            engine(mutableListOf(), null).read(signedExport().toMutableMap()).shouldBeInstanceOf<BackupReadResult.Ready>()
        }
    },
)
