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

/** The most uncompressed bytes one section file holds. */
internal const val MAX_SECTION_BYTES: Long = 16L * 1024 * 1024

/** The most uncompressed bytes all files of a backup hold. */
internal const val MAX_TOTAL_BYTES: Long = 32L * 1024 * 1024

/** The most uncompressed bytes `manifest.json` holds. */
internal const val MAX_MANIFEST_BYTES: Long = 64L * 1024

/** The deepest nesting of arrays and objects a JSON file of the backup holds. */
internal const val MAX_NESTING_DEPTH: Int = 64

/** The most bytes one entry of a section file holds, counted between two separators at the depth of the entries array. */
internal const val MAX_ENTRY_BYTES: Int = 1024 * 1024

/** The most values one entry of a section file holds; each opening bracket and each comma inside an entry counts as one. */
internal const val MAX_ENTRY_VALUES: Int = 100_000

// The depth of the array that holds the entries; the file object is depth 1.
private const val ENTRIES_DEPTH = 2

/**
 * Whether the JSON text in these bytes stays within [MAX_NESTING_DEPTH], [MAX_ENTRY_VALUES] and [MAX_ENTRY_BYTES]. It
 * scans the bytes and builds no tree, so a file of 16 MiB of `[` or `0,`, or of one huge string, ends here instead of in
 * an OutOfMemoryError while decoding.
 */
internal fun ByteArray.withinJsonLimits(): Boolean {
    val scanner = JsonScanner()
    return all(scanner::accept)
}

/**
 * Scans JSON text byte by byte. The value and byte counts restart at each separator at the depth of the entries array or
 * above: a comma, or the bracket that opens the entries array. So the byte count of an entry runs between two
 * separators.
 */
internal class JsonScanner {
    private var depth = 0
    private var values = 0
    private var entryBytes = 0
    private var inString = false
    private var escaped = false

    /** Reads one byte of the text; false once the text exceeds a limit. */
    fun accept(byte: Byte): Boolean {
        val char = byte.toInt().toChar()
        entryBytes++
        if (inString) readStringChar(char) else readStructureChar(char)
        return depth <= MAX_NESTING_DEPTH && values <= MAX_ENTRY_VALUES && entryBytes <= MAX_ENTRY_BYTES
    }

    private fun readStringChar(char: Char) {
        when {
            escaped -> escaped = false
            char == '\\' -> escaped = true
            char == '"' -> inString = false
        }
    }

    private fun readStructureChar(char: Char) {
        when (char) {
            '"' -> inString = true
            '[', '{' -> {
                depth++
                if (depth > ENTRIES_DEPTH) values++ else entryBytes = 0
            }
            ']', '}' -> {
                depth--
                if (depth < ENTRIES_DEPTH) entryBytes = 0
            }
            ',' -> if (depth > ENTRIES_DEPTH) values++ else startNextEntry()
        }
    }

    private fun startNextEntry() {
        values = 0
        entryBytes = 0
    }
}

/** The text of these bytes, or null if they exceed the JSON limits. */
internal fun ByteArray.jsonTextWithinLimits(): String? = takeIf { it.withinJsonLimits() }?.decodeToString()

/** Decodes these bytes with [strategy], or returns null if they exceed the JSON limits or do not decode. */
internal fun <T : Any> ByteArray.decodeWithinLimits(strategy: DeserializationStrategy<T>): T? =
    jsonTextWithinLimits()?.let { text -> decodeOrNull { backupJson.decodeFromString(strategy, text) } }
