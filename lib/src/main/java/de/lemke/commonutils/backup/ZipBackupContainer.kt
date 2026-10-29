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
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val MAX_SECTION_BYTES = 16L * 1024 * 1024
private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
private const val ARCHIVE_OVERHEAD_BYTES = 1L * 1024 * 1024
private const val FILES_PREFIX = "files/"
private val SECTION_NAME = Regex("sections/[a-z0-9_-]+\\.json")
private val FILE_SEGMENT = Regex("[A-Za-z0-9._-]+")

/** The largest zip file the import reads: the uncompressed cap plus the headers and the DEFLATE framing of an incompressible file. */
internal const val MAX_ARCHIVE_BYTES: Long = MAX_TOTAL_BYTES + ARCHIVE_OVERHEAD_BYTES

/** What [ZipBackupContainer.read] found. */
internal sealed interface ZipRead {
    /** The accepted entries by name. Entries under `files/` are drained and left out. */
    data class Files(val files: Map<String, ByteArray>) : ZipRead

    /** Not a zip, or an entry with a name the container refuses or cannot decode. */
    data object Invalid : ZipRead

    /** The uncompressed bytes of a section or of the whole backup passed the cap. */
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

    /**
     * Reads the entries of [input]. The uncompressed bytes of every entry, a skipped `files/` entry too, count toward
     * [maxTotalBytes]; a section entry also stays within [maxSectionBytes].
     */
    fun read(
        input: InputStream,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
        maxSectionBytes: Long = MAX_SECTION_BYTES,
    ): ZipRead =
        try {
            val zip = ZipInputStream(input)
            val collector = EntryCollector(maxTotalBytes, maxSectionBytes)
            val refusal = generateSequence { zip.nextEntry }.firstNotNullOfOrNull { collector.accept(zip, it.name) }
            refusal ?: if (collector.files.isEmpty()) ZipRead.Invalid else ZipRead.Files(collector.files)
        } catch (_: IOException) {
            ZipRead.Invalid
        } catch (_: IllegalArgumentException) {
            ZipRead.Invalid
        }
}

private class EntryCollector(
    private var remaining: Long,
    private val maxSectionBytes: Long,
) {
    val files = linkedMapOf<String, ByteArray>()

    /** Takes the entry [name] that [zip] is positioned on; returns the refusal, or null if the entry is fine. */
    fun accept(
        zip: InputStream,
        name: String,
    ): ZipRead? {
        val acceptable = isAcceptedName(name) && name !in files
        val bytes =
            if (acceptable) {
                zip.readAtMost(
                    if (SECTION_NAME.matches(name)) minOf(remaining, maxSectionBytes) else remaining,
                )
            } else {
                null
            }
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
