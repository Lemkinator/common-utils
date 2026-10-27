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

import de.lemke.commonutils.backup.core.MANIFEST_ENTRY
import de.lemke.commonutils.backup.core.SIGNATURE_ENTRY
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
private const val BUFFER_SIZE = 8 * 1024
private const val FILES_PREFIX = "files/"
private val SECTION_NAME = Regex("sections/[a-z0-9_-]+\\.json")
private val FILE_SEGMENT = Regex("[A-Za-z0-9._-]+")

/** What [ZipBackupContainer.read] found. */
internal sealed interface ZipRead {
    /** The accepted entries by name. Entries under `files/` are drained and left out. */
    data class Files(val files: Map<String, ByteArray>) : ZipRead

    /** Not a zip, or an entry with a name the container refuses. */
    data object Invalid : ZipRead

    /** The uncompressed bytes passed the cap. */
    data object TooLarge : ZipRead
}

/** Zips and unzips the entries of a backup. The reader bounds names and uncompressed size, so a hostile file refuses cleanly. */
internal object ZipBackupContainer {
    /** Writes [files] with `manifest.json` first, every entry DEFLATE-compressed. */
    fun write(
        files: Map<String, ByteArray>,
        output: OutputStream,
    ) {
        ZipOutputStream(output).use { zip ->
            files.entries.sortedBy { it.key != MANIFEST_ENTRY }.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /** Reads the entries of [input]. The uncompressed bytes of every entry, a skipped `files/` entry too, count toward [maxTotalBytes]. */
    fun read(
        input: InputStream,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
    ): ZipRead =
        try {
            val zip = ZipInputStream(input)
            val collector = EntryCollector(maxTotalBytes)
            val refusal = generateSequence { zip.nextEntry }.firstNotNullOfOrNull { collector.accept(zip, it.name) }
            refusal ?: if (collector.files.isEmpty()) ZipRead.Invalid else ZipRead.Files(collector.files)
        } catch (_: IOException) {
            ZipRead.Invalid
        }
}

private class EntryCollector(private var remaining: Long) {
    val files = linkedMapOf<String, ByteArray>()

    /** Takes the entry [name] that [zip] is positioned on; returns the refusal, or null if the entry is fine. */
    fun accept(
        zip: InputStream,
        name: String,
    ): ZipRead? {
        val acceptable = isAcceptedName(name) && name !in files
        val bytes = if (acceptable) drain(zip, remaining) else null
        return when {
            !acceptable -> ZipRead.Invalid
            bytes == null -> ZipRead.TooLarge
            else -> {
                remaining -= bytes.size
                if (!name.startsWith(FILES_PREFIX)) files[name] = bytes
                null
            }
        }
    }
}

private fun isAcceptedName(name: String): Boolean =
    name == MANIFEST_ENTRY ||
        name == SIGNATURE_ENTRY ||
        SECTION_NAME.matches(name) ||
        (name.startsWith(FILES_PREFIX) && name.removePrefix(FILES_PREFIX).split('/').all { it != ".." && FILE_SEGMENT.matches(it) })

/** Reads the current entry, or returns null once it passes [limit] bytes; it never holds more than [limit] plus one buffer. */
private fun drain(
    zip: InputStream,
    limit: Long,
): ByteArray? {
    val bytes = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    var total = 0L
    var read = zip.read(buffer)
    while (read >= 0 && total <= limit) {
        total += read
        bytes.write(buffer, 0, read)
        read = zip.read(buffer)
    }
    return bytes.toByteArray().takeIf { total <= limit }
}
