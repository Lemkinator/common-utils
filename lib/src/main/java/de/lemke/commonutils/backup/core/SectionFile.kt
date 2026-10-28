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

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The most entries a section file holds; a larger file fails to decode before its tree grows past this many entries. */
internal const val MAX_SECTION_ENTRIES: Int = 100_000

private const val ENTRIES_KEY = "entries"

private val entriesDescriptor = ListSerializer(JsonElement.serializer()).descriptor

/** The text of a section file: an object with the [entries] array. */
internal fun sectionFileText(entries: List<JsonElement>): String =
    backupJson.encodeToString(JsonObject.serializer(), JsonObject(mapOf(ENTRIES_KEY to JsonArray(entries))))

internal class TooManyEntriesException : SerializationException("A section holds more than $MAX_SECTION_ENTRIES entries")

/**
 * Reads a section file entry by entry: each element is decoded, upgraded from [fromVersion] and decoded to `E`, and its
 * tree is dropped before the next one, so the peak is one entry tree plus the typed list. An entry that does not decode
 * counts as skipped. It throws [TooManyEntriesException] on entry [MAX_SECTION_ENTRIES] + 1.
 */
internal class SectionDecoder<E : Any>(
    private val fromVersion: Int,
    private val section: BackupSection<E>,
) : DeserializationStrategy<ReadSection<E>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SectionFile") { element(ENTRIES_KEY, entriesDescriptor) }

    override fun deserialize(decoder: Decoder): ReadSection<E> =
        decoder.decodeStructure(descriptor) {
            var read: ReadSection<E>? = null
            var index = decodeElementIndex(descriptor)
            while (index != CompositeDecoder.DECODE_DONE) {
                read = decodeSerializableElement(descriptor, index, Entries())
                index = decodeElementIndex(descriptor)
            }
            read ?: throw MissingFieldException(ENTRIES_KEY, descriptor.serialName)
        }

    private inner class Entries : DeserializationStrategy<ReadSection<E>> {
        override val descriptor: SerialDescriptor = entriesDescriptor

        override fun deserialize(decoder: Decoder): ReadSection<E> =
            decoder.decodeStructure(descriptor) {
                val entries = mutableListOf<E>()
                var skipped = 0
                var index = decodeElementIndex(descriptor)
                while (index != CompositeDecoder.DECODE_DONE) {
                    if (entries.size + skipped == MAX_SECTION_ENTRIES) throw TooManyEntriesException()
                    val element = decodeSerializableElement(descriptor, index, JsonElement.serializer())
                    val entry = decodeEntry(element, fromVersion, section)
                    if (entry == null) skipped++ else entries += entry
                    index = decodeElementIndex(descriptor)
                }
                ReadSection(section, entries, skipped)
            }
    }
}
