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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

private const val MIB = 1024 * 1024

private fun readFiles(
    bytes: ByteArray,
    cap: Long? = null,
    sectionCap: Long? = null,
): ZipRead =
    when {
        cap == null -> ZipBackupContainer.read(ByteArrayInputStream(bytes))
        sectionCap == null -> ZipBackupContainer.read(ByteArrayInputStream(bytes), cap)
        else -> ZipBackupContainer.read(ByteArrayInputStream(bytes), cap, sectionCap)
    }

private fun filesOf(read: ZipRead): Map<String, String> =
    read.shouldBeInstanceOf<ZipRead.Files>().files.mapValues { it.value.decodeToString() }

private fun written(files: Map<String, ByteArray>): ByteArray =
    ByteArrayOutputStream()
        .also {
            ZipBackupContainer.write(files, it)
        }.toByteArray()

class ZipBackupContainerTest : ShouldSpec(
    {
        should("round-trip files with manifest first") {
            val bytes = written(mapOf("sections/items.json" to "S".encodeToByteArray(), "manifest.json" to "M".encodeToByteArray()))
            val zip = ZipInputStream(ByteArrayInputStream(bytes))
            generateSequence { zip.nextEntry?.name }.toList() shouldContainExactly listOf("manifest.json", "sections/items.json")
            filesOf(readFiles(bytes)) shouldBe mapOf("manifest.json" to "M", "sections/items.json" to "S")
        }
        should("round-trip a signature.json entry") {
            val bytes = written(mapOf("manifest.json" to "M".encodeToByteArray(), "signature.json" to "G".encodeToByteArray()))
            filesOf(readFiles(bytes)) shouldBe mapOf("manifest.json" to "M", "signature.json" to "G")
        }
        should("write every entry with the DEFLATED method") {
            val bytes = written(mapOf("manifest.json" to "M".encodeToByteArray(), "sections/a.json" to "A".encodeToByteArray()))
            val zip = ZipInputStream(ByteArrayInputStream(bytes))
            generateSequence { zip.nextEntry?.method }.toList() shouldContainExactly listOf(ZipEntry.DEFLATED, ZipEntry.DEFLATED)
        }
        should("return Invalid for a non-zip stream") {
            readFiles("this is not a zip".encodeToByteArray()) shouldBe ZipRead.Invalid
        }
        should("return Invalid for a truncated zip") {
            val whole = zipOf("manifest.json" to ByteArray(5000) { (it * 31).toByte() })
            readFiles(whole.copyOf(whole.size / 2)) shouldBe ZipRead.Invalid
        }
        should("return Invalid for an entry named ../evil.json") {
            readFiles(zipOf("manifest.json" to byteArrayOf(1), "../evil.json" to byteArrayOf(1))) shouldBe ZipRead.Invalid
        }
        should("return Invalid for an entry named files/../evil.json") {
            readFiles(zipOf("manifest.json" to byteArrayOf(1), "files/../evil.json" to byteArrayOf(1))) shouldBe ZipRead.Invalid
        }
        should("return Invalid for an unknown entry name") {
            readFiles(zipOf("manifest.json" to byteArrayOf(1), "notes.txt" to byteArrayOf(1))) shouldBe ZipRead.Invalid
        }
        should("return Invalid for a files entry with an empty segment") {
            readFiles(zipOf("manifest.json" to byteArrayOf(1), "files//a.png" to byteArrayOf(1))) shouldBe ZipRead.Invalid
        }
        should("return Invalid for a duplicate entry name") {
            val zip = zipOf("sections/aa.json" to byteArrayOf(1), "sections/bb.json" to byteArrayOf(2))
            val text = String(zip, Charsets.ISO_8859_1).replace("sections/bb.json", "sections/aa.json")
            readFiles(text.toByteArray(Charsets.ISO_8859_1)) shouldBe ZipRead.Invalid
        }
        should("return Invalid for an entry name that is no valid UTF-8") {
            val zip = zipOf("sections/aa.json" to byteArrayOf(1))
            val name = "sections/aa.json".encodeToByteArray()
            val at = (0..zip.size - name.size).first { zip.copyOfRange(it, it + name.size).contentEquals(name) }
            zip[at + name.size - 1] = 0xFF.toByte()
            readFiles(zip) shouldBe ZipRead.Invalid
        }
        should("return Invalid for a zip without entries") {
            readFiles(zipOf()) shouldBe ZipRead.Invalid
        }
        should("skip an entry under files/ and return the rest") {
            val read = readFiles(zipOf("manifest.json" to "M".encodeToByteArray(), "files/images/a-1.png" to byteArrayOf(1, 2, 3)))
            filesOf(read) shouldBe mapOf("manifest.json" to "M")
        }
        should("return TooLarge when uncompressed bytes exceed the cap") {
            val zip = zipOf("manifest.json" to ByteArray(100_000))
            (zip.size < 1000) shouldBe true
            readFiles(zip, cap = 1000) shouldBe ZipRead.TooLarge
        }
        should("accept 16 MiB uncompressed in one section by default and refuse one byte more") {
            val cap = 16 * MIB
            filesOf(readFiles(zipOf("sections/a.json" to ByteArray(cap)))).getValue("sections/a.json").length shouldBe cap
            readFiles(zipOf("sections/a.json" to ByteArray(cap + 1))) shouldBe ZipRead.TooLarge
        }
        should("accept a manifest of 64 KiB and refuse one byte more") {
            filesOf(readFiles(zipOf("manifest.json" to ByteArray(64 * 1024)))).getValue("manifest.json").length shouldBe 64 * 1024
            readFiles(zipOf("manifest.json" to ByteArray(64 * 1024 + 1))) shouldBe ZipRead.TooLarge
        }
        should("accept a signature of 4 KiB and refuse one byte more") {
            val manifest = "manifest.json" to byteArrayOf(1)
            filesOf(readFiles(zipOf(manifest, "signature.json" to ByteArray(4 * 1024)))).getValue("signature.json").length shouldBe 4 * 1024
            readFiles(zipOf(manifest, "signature.json" to ByteArray(4 * 1024 + 1))) shouldBe ZipRead.TooLarge
        }
        should("read two sections of 16 MiB each") {
            val read = readFiles(zipOf("sections/a.json" to ByteArray(16 * MIB), "sections/b.json" to ByteArray(16 * MIB)))
            read.shouldBeInstanceOf<ZipRead.Files>().files.mapValues { it.value.size } shouldBe
                mapOf("sections/a.json" to 16 * MIB, "sections/b.json" to 16 * MIB)
        }
        should("accept 64 MiB uncompressed in all by default and refuse one byte more") {
            val atCap = zipOf("files/a.bin" to ByteArray(48 * MIB), "sections/a.json" to ByteArray(16 * MIB))
            filesOf(readFiles(atCap)).keys shouldBe setOf("sections/a.json")
            val overCap = zipOf("files/a.bin" to ByteArray(48 * MIB + 1), "sections/a.json" to ByteArray(16 * MIB))
            readFiles(overCap) shouldBe ZipRead.TooLarge
        }
        should("refuse 64 MiB and one byte spread over four sections and a manifest") {
            val sections = Array(4) { "sections/s$it.json" to ByteArray(16 * MIB) }
            readFiles(zipOf(*sections, "manifest.json" to byteArrayOf(1))) shouldBe ZipRead.TooLarge
        }
        should("fit the largest accepted backup of incompressible bytes into the archive limit") {
            val random = Random(7)
            val sections = Array(4) { "sections/s$it.json" to ByteArray(16 * MIB).also(random::nextBytes) }
            val zip = written(mapOf(*sections))
            (zip.size > 64 * MIB) shouldBe true
            (zip.size <= MAX_ARCHIVE_BYTES) shouldBe true
            filesOf(readFiles(zip)).keys shouldBe setOf("sections/s0.json", "sections/s1.json", "sections/s2.json", "sections/s3.json")
        }
        should("apply the section cap to sections only") {
            val zip = zipOf("manifest.json" to ByteArray(600), "sections/a.json" to ByteArray(400))
            filesOf(readFiles(zip, cap = 1000, sectionCap = 500)).keys shouldBe setOf("manifest.json", "sections/a.json")
            readFiles(zipOf("sections/a.json" to ByteArray(501)), cap = 1000, sectionCap = 500) shouldBe ZipRead.TooLarge
        }
        should("count a skipped files/ entry toward the cap") {
            readFiles(zipOf("files/big.bin" to ByteArray(2000), "manifest.json" to byteArrayOf(1)), cap = 1000) shouldBe ZipRead.TooLarge
        }
        should("count every entry toward one shared cap") {
            val zip = zipOf("manifest.json" to ByteArray(600), "sections/a.json" to ByteArray(600))
            readFiles(zip, cap = 1000) shouldBe ZipRead.TooLarge
            filesOf(readFiles(zip, cap = 1200)).keys shouldBe setOf("manifest.json", "sections/a.json")
        }
        should("accept a zip file of exactly the archive limit and refuse one byte more") {
            val zip = zipOf("manifest.json" to "M".encodeToByteArray())
            filesOf(ZipBackupContainer.read(ByteArrayInputStream(zip), maxArchiveBytes = zip.size.toLong())).keys shouldBe
                setOf("manifest.json")
            ZipBackupContainer.read(ByteArrayInputStream(zip), maxArchiveBytes = zip.size - 1L) shouldBe ZipRead.TooLarge
        }
        should("refuse trailing bytes after the last entry that pass the archive limit") {
            val zip = zipOf("manifest.json" to "M".encodeToByteArray())
            ZipBackupContainer.read(ByteArrayInputStream(zip + ByteArray(5000)), maxArchiveBytes = zip.size + 4999L) shouldBe
                ZipRead.TooLarge
        }
        should("stop pulling from a stream that never ends once it passes the archive limit") {
            val zip = zipOf("manifest.json" to "M".encodeToByteArray())
            var served = 0L
            val endless =
                object : InputStream() {
                    override fun read(): Int = error("The container reads in blocks")

                    override fun read(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ): Int {
                        check(served < 1000 + 8 * 1024) { "Read past the archive limit" }
                        for (i in 0 until len) b[off + i] = if (served + i < zip.size) zip[(served + i).toInt()] else 0
                        served += len
                        return len
                    }
                }

            ZipBackupContainer.read(endless, maxArchiveBytes = 1000) shouldBe ZipRead.TooLarge
            (served in 1001..1000 + 8 * 1024) shouldBe true
        }
        should("check the job before each entry and stop at a throw") {
            val zip = zipOf("manifest.json" to byteArrayOf(1), "sections/a.json" to byteArrayOf(2), "sections/b.json" to byteArrayOf(3))
            var checks = 0

            shouldThrow<CancellationException> {
                ZipBackupContainer.read(ByteArrayInputStream(zip)) { if (++checks == 2) throw CancellationException("canceled") }
            }
            checks shouldBe 2
        }
    },
)
