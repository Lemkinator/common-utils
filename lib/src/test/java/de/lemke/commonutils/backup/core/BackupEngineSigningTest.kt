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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private class FoldSigner(override val keyId: String = "test") : BackupSigner {
    override fun mac(payload: ByteArray): ByteArray {
        val out = ByteArray(32)
        payload.forEachIndexed { index, byte -> out[index % 32] = (out[index % 32] + byte * (index % 251 + 1)).toByte() }
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
    engine(mutableListOf(Item("a", 1)), signer).export()

// name, 0x00, length as 8 bytes big-endian, bytes; manifest.json first, then the section entries by name
private fun handBuiltPayload(files: Map<String, ByteArray>): ByteArray {
    fun framed(name: String): ByteArray {
        val bytes = files.getValue(name)
        require(bytes.size < 256) { "The test export must stay below 256 bytes per entry" }
        return name.encodeToByteArray() + byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, bytes.size.toByte()) + bytes
    }
    return framed("manifest.json") + framed("sections/items.json")
}

private fun refusalOf(
    files: Map<String, ByteArray>,
    signer: BackupSigner? = FoldSigner(),
): BackupRefusal = engine(mutableListOf(), signer).read(files).shouldBeInstanceOf<BackupReadResult.Refused>().reason

class BackupEngineSigningTest : ShouldSpec(
    {
        should("export with a signer writes signature.json with alg, keyId and a hex mac") {
            val files = signedExport()
            val signature = backupJson.parseToJsonElement(files.getValue(SIGNATURE_ENTRY).decodeToString()).jsonObject
            signature.getValue("alg").jsonPrimitive.content shouldBe "HMAC-SHA256"
            signature.getValue("keyId").jsonPrimitive.content shouldBe "test"
            signature.getValue("mac").jsonPrimitive.content shouldBe FoldSigner().mac(handBuiltPayload(files)).toHex()
        }
        should("export without a signer writes no signature entry") {
            (SIGNATURE_ENTRY in engine(mutableListOf(), null).export()) shouldBe false
        }
        should("a signing engine reads its own export") {
            engine(mutableListOf(), FoldSigner()).read(signedExport()).shouldBeInstanceOf<BackupReadResult.Ready>()
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
            val files = (signedExport() - SIGNATURE_ENTRY)
            val other =
                files
                    .getValue(MANIFEST_ENTRY)
                    .decodeToString()
                    .replace("oneurl", "sudoku")
                    .encodeToByteArray()
            refusalOf(files + (MANIFEST_ENTRY to other)) shouldBe BackupRefusal.OtherApp("sudoku")
        }
        should("an engine without a signer reads a signed file") {
            engine(mutableListOf(), null).read(signedExport()).shouldBeInstanceOf<BackupReadResult.Ready>()
        }
    },
)
