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

class BackupSignatureTest : ShouldSpec(
    {
        should("order payload entries by name and frame each with its length") {
            val files = mapOf("sections/b.json" to "B".encodeToByteArray(), MANIFEST_ENTRY to "MM".encodeToByteArray())
            val expected =
                "manifest.json".encodeToByteArray() + 0.toByte() + byteArrayOf(0, 0, 0, 0, 0, 0, 0, 2) + "MM".encodeToByteArray() +
                    "sections/b.json".encodeToByteArray() + 0.toByte() + byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1) + "B".encodeToByteArray()
            signedPayload(files).toHex() shouldBe expected.toHex()
        }
        should("leave out files/ entries and signature.json of the payload") {
            val files =
                mapOf(
                    MANIFEST_ENTRY to "M".encodeToByteArray(),
                    SIGNATURE_ENTRY to "S".encodeToByteArray(),
                    "files/photo.png" to "P".encodeToByteArray(),
                    "sections/notes.txt" to "N".encodeToByteArray(),
                )
            signedPayload(files).toHex() shouldBe signedPayload(mapOf(MANIFEST_ENTRY to "M".encodeToByteArray())).toHex()
        }
        should("frame a length above one byte as big-endian") {
            val payload = signedPayload(mapOf(MANIFEST_ENTRY to ByteArray(258)))
            payload.sliceArray(14 until 22).toHex() shouldBe "0000000000000102"
        }
        should("write lowercase hex with leading zeros") {
            byteArrayOf(0, 10, -1, 0x7f).toHex() shouldBe "000aff7f"
        }
        should("compare equal arrays as equal") {
            constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2)) shouldBe true
        }
        should("compare arrays of another content or length as different") {
            constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 3)) shouldBe false
            constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1)) shouldBe false
        }
    },
)
