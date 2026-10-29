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
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun decoder(ensureActive: () -> Unit = {}) = SectionDecoder(1, MemorySection(), ensureActive)

class SectionFileTest : ShouldSpec(
    {
        should("encode a section file as an object with an entries array") {
            sectionFileText(listOf(JsonObject(mapOf("a" to JsonPrimitive(1))))) shouldBe """{"entries":[{"a":1}]}"""
        }
        should("decode a section file with an unknown key next to entries") {
            val read = backupJson.decodeFromString(decoder(), """{"entries":[{"key":"a","value":1}],"deleted":[]}""")
            read.entries shouldBe listOf(Item("a", 1))
            read.skipped shouldBe 0
        }
        should("count an entry that does not decode as skipped") {
            val read = backupJson.decodeFromString(decoder(), """{"entries":[{"key":"a"},5,{"key":"b","value":2}]}""")
            read.entries shouldBe listOf(Item("a", 0), Item("b", 2))
            read.skipped shouldBe 1
        }
        should("check the job before each entry") {
            var checks = 0
            backupJson.decodeFromString(decoder { checks++ }, """{"entries":[{"key":"a"},5,{"key":"b"}]}""")
            checks shouldBe 3
        }
        should("refuse to decode a section file without entries") {
            shouldThrow<SerializationException> { backupJson.decodeFromString(decoder(), """{"deleted":[]}""") }
        }
    },
)
