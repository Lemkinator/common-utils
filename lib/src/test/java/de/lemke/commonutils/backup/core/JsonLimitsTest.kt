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

private const val FILE_START = "{\"entries\":["
private const val FILE_END = "]}"
private const val ENTRY_START = "{\"key\":\""
private const val ENTRY_END = "\"}"

private fun bytesOf(text: String) = text.encodeToByteArray()

private fun entryOfBytes(size: Int): String = ENTRY_START + "a".repeat(size - ENTRY_START.length - ENTRY_END.length) + ENTRY_END

private fun fileOf(vararg entries: String): ByteArray = bytesOf(FILE_START + entries.joinToString(",") + FILE_END)

class JsonLimitsTest : ShouldSpec(
    {
        should("accept an entry of exactly the byte limit and refuse one byte more") {
            fileOf(entryOfBytes(MAX_ENTRY_BYTES)).withinJsonLimits() shouldBe true
            fileOf(entryOfBytes(MAX_ENTRY_BYTES + 1)).withinJsonLimits() shouldBe false
        }
        should("count the bytes of each entry on its own") {
            fileOf(entryOfBytes(MAX_ENTRY_BYTES), entryOfBytes(MAX_ENTRY_BYTES)).withinJsonLimits() shouldBe true
        }
        should("count an element that is no object like an entry") {
            val string = "\"" + "a".repeat(MAX_ENTRY_BYTES) + "\""

            fileOf(string).withinJsonLimits() shouldBe false
        }
        should("count a member of the file object like an entry") {
            val member = "{\"extra\":\"" + "a".repeat(MAX_ENTRY_BYTES) + "\",\"entries\":[]}"

            bytesOf(member).withinJsonLimits() shouldBe false
        }
        should("stop at the first byte past the limit of an entry that never ends") {
            val scanner = JsonScanner()
            val endless = bytesOf(FILE_START + ENTRY_START).asSequence() + generateSequence { 'a'.code.toByte() }
            var fed = 0
            for (byte in endless) {
                fed++
                if (!scanner.accept(byte)) break
            }

            fed shouldBe FILE_START.length + MAX_ENTRY_BYTES + 1
        }
    },
)
