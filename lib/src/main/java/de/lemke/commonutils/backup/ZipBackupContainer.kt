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

internal const val MAX_SECTION_BYTES = 16L * 1024 * 1024
internal const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
private const val MAX_MANIFEST_BYTES = 64L * 1024
private const val MAX_SIGNATURE_BYTES = 4L * 1024
private const val ARCHIVE_OVERHEAD_BYTES = 1L * 1024 * 1024
private const val FILES_PREFIX = "files/"
private val SECTION_NAME = Regex("sections/[a-z0-9_-]+\\.json")
private val FILE_SEGMENT = Regex("[A-Za-z0-9._-]+")

/** The largest zip file the import streams: the uncompressed cap plus the headers and the DEFLATE framing of an incompressible file. */
internal const val MAX_ARCHIVE_BYTES: Long = MAX_TOTAL_BYTES + ARCHIVE_OVERHEAD_BYTES

/** What [ZipBackupContainer.read] found. */
internal sealed interface ZipRead {
    /** The accepted entries by name. Entries under `files/` are drained and left out. Its reader empties the map. */
    data class Files(val files: MutableMap<String, ByteArray>) : ZipRead

    /** Not a zip, or an entry with a name the container refuses or cannot decode. */
    data object Invalid : ZipRead

    /** The uncompressed bytes of a section or of the whole backup, or the zip file itself, passed the cap. */
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
     * Streams the entries of [input] without holding the zip file. The uncompressed bytes of every entry, a dropped
     * `files/` entry too, count toward [maxTotalBytes]; a section entry also stays within [maxSectionBytes], and the whole
     * of [input], trailing bytes included, within [maxArchiveBytes]. [ensureActive] runs before each entry; a throw from it
     * ends the read.
     */
    fun read(
        input: InputStream,
        maxTotalBytes: Long = MAX_TOTAL_BYTES,
        maxSectionBytes: Long = MAX_SECTION_BYTES,
        maxArchiveBytes: Long = MAX_ARCHIVE_BYTES,
        ensureActive: () -> Unit = {},
    ): ZipRead =
        try {
            val limited = LimitedInputStream(input, maxArchiveBytes)
            ZipInputStream(limited).use { zip ->
                val collector = EntryCollector(maxTotalBytes, maxSectionBytes)
                val refusal =
                    generateSequence { zip.nextEntry }
                        .firstNotNullOfOrNull {
                            ensureActive()
                            collector.accept(zip, it.name)
                        }
                when {
                    refusal != null -> refusal
                    collector.files.isEmpty() -> ZipRead.Invalid
                    else -> {
                        limited.dropAtMost(Long.MAX_VALUE)
                        ZipRead.Files(collector.files)
                    }
                }
            }
        } catch (_: LimitExceededException) {
            ZipRead.TooLarge
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
        val size = if (acceptable) take(zip, name) else null
        return when {
            !acceptable -> ZipRead.Invalid
            size == null -> ZipRead.TooLarge
            else -> {
                remaining -= size
                null
            }
        }
    }

    private fun entryCap(name: String): Long =
        when {
            name == MANIFEST_ENTRY -> MAX_MANIFEST_BYTES
            name == SIGNATURE_ENTRY -> MAX_SIGNATURE_BYTES
            SECTION_NAME.matches(name) -> maxSectionBytes
            else -> Long.MAX_VALUE
        }

    /** Keeps the bytes of the entry unless it lies under `files/`, which is counted and dropped; null once it passes its cap. */
    private fun take(
        zip: InputStream,
        name: String,
    ): Long? {
        val limit = minOf(remaining, entryCap(name))
        return if (name.startsWith(FILES_PREFIX)) {
            zip.dropAtMost(limit)
        } else {
            zip
                .readAtMost(limit)
                ?.also { files[name] = it }
                ?.size
                ?.toLong()
        }
    }
}

private fun isAcceptedName(name: String): Boolean =
    name == MANIFEST_ENTRY ||
        name == SIGNATURE_ENTRY ||
        SECTION_NAME.matches(name) ||
        (name.startsWith(FILES_PREFIX) && name.removePrefix(FILES_PREFIX).split('/').all { it != ".." && FILE_SEGMENT.matches(it) })
