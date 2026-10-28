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

/** The deepest nesting of arrays and objects a JSON file of the backup holds. */
internal const val MAX_NESTING_DEPTH: Int = 64

/** The most values one entry of a section file holds; each opening bracket and each comma inside an entry counts as one. */
internal const val MAX_ENTRY_VALUES: Int = 100_000

// The depth of the array that holds the entries; the file object is depth 1.
private const val ENTRIES_DEPTH = 2

/**
 * Whether the JSON text in these bytes stays within [MAX_NESTING_DEPTH] and [MAX_ENTRY_VALUES]. It scans the bytes and
 * builds no tree, so a file of 16 MiB of `[` or `0,` ends here instead of in an OutOfMemoryError while decoding. A comma
 * at the depth of the entries array starts the next entry and resets the count.
 */
internal fun ByteArray.withinJsonLimits(): Boolean {
    val scanner = JsonScanner()
    return all { scanner.accept(it.toInt().toChar()) }
}

private class JsonScanner {
    private var depth = 0
    private var values = 0
    private var inString = false
    private var escaped = false

    /** Reads one character of the text; false once the text exceeds a limit. */
    fun accept(char: Char): Boolean {
        if (inString) readStringChar(char) else readStructureChar(char)
        return depth <= MAX_NESTING_DEPTH && values <= MAX_ENTRY_VALUES
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
                if (depth > ENTRIES_DEPTH) values++
            }
            ']', '}' -> depth--
            ',' -> if (depth > ENTRIES_DEPTH) values++ else values = 0
        }
    }
}

/** Decodes these bytes with [strategy], or returns null if they exceed the JSON limits or do not decode. */
internal fun <T : Any> ByteArray.decodeWithinLimits(strategy: DeserializationStrategy<T>): T? =
    takeIf { it.withinJsonLimits() }?.let { decodeOrNull { backupJson.decodeFromString(strategy, it.decodeToString()) } }
