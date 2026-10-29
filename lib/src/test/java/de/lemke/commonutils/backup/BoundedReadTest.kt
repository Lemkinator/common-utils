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
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream

private fun streamOf(size: Int) = ByteArrayInputStream(ByteArray(size) { it.toByte() })

/** Serves zeros without end and records what the reader asks for. */
private class EndlessStream : InputStream() {
    var served = 0L
    var largestRequest = 0

    override fun read(): Int = error("The reader reads in blocks")

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        largestRequest = maxOf(largestRequest, len)
        served += len
        return len
    }
}

/** Serves a stream of zeros one byte per read. */
private class TrickleStream(private var remaining: Int) : InputStream() {
    override fun read(): Int = error("The reader reads in blocks")

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        if (remaining == 0) return -1
        remaining--
        b[off] = 0
        return 1
    }
}

/** Records the array of each block read. */
private class RecordingStream(input: InputStream) : FilterInputStream(input) {
    val arrays = mutableListOf<ByteArray>()

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        arrays += b
        return super.read(b, off, len)
    }
}

class BoundedReadTest : ShouldSpec(
    {
        should("read a stream of exactly the limit and return null for one byte more") {
            streamOf(20_000).readAtMost(20_000)?.size shouldBe 20_000
            streamOf(20_001).readAtMost(20_000) shouldBe null
        }
        should("request one byte past the limit and no block larger than the buffer from a stream that never ends") {
            val read = EndlessStream()

            read.readAtMost(20_000) shouldBe null
            read.served shouldBe 20_001L
            read.largestRequest shouldBe 8 * 1024

            val drop = EndlessStream()
            drop.dropAtMost(20_000) shouldBe null
            drop.served shouldBe 20_001L
        }
        should("request only one byte for a limit of zero") {
            val read = EndlessStream()

            read.readAtMost(0) shouldBe null
            read.served shouldBe 1L
        }
        should("return the bytes in order across blocks") {
            val bytes = ByteArray(20_000) { (it * 7).toByte() }

            ByteArrayInputStream(bytes).readAtMost(20_000)!!.toList() shouldBe bytes.toList()
        }
        should("keep exact-size blocks of the bytes read, however short each read is") {
            TrickleStream(20_000).readBlocks(20_000)?.map { it.size } shouldBe listOf(8192, 8192, 3616)
            TrickleStream(16_384).readBlocks(20_000)?.map { it.size } shouldBe listOf(8192, 8192)
            TrickleStream(100).readBlocks(20_000)?.map { it.size } shouldBe listOf(100)
        }
        should("keep no block of a stream that passes the limit") {
            TrickleStream(20_001).readBlocks(20_000) shouldBe null
            streamOf(0).readBlocks(10) shouldBe emptyList()
        }
        should("read an empty stream as an empty array") {
            streamOf(0).readAtMost(10)?.size shouldBe 0
        }
        should("count a stream of exactly the limit and return null for one byte more") {
            streamOf(20_000).dropAtMost(20_000) shouldBe 20_000L
            streamOf(20_001).dropAtMost(20_000) shouldBe null
        }
        should("count a stream against a limit of the largest long") {
            streamOf(20_000).dropAtMost(Long.MAX_VALUE) shouldBe 20_000L
        }
        should("pass the bytes of a limited stream up to its limit") {
            val limited = LimitedInputStream(streamOf(300), 300)

            limited.read() shouldBe 0
            limited.readNBytes(299).size shouldBe 299
            limited.read() shouldBe -1
        }
        should("throw on the first byte after the limit, read singly or in a block") {
            val single = LimitedInputStream(streamOf(10), 2)
            single.read() shouldBe 0
            single.read() shouldBe 1
            shouldThrow<LimitExceededException> { single.read() }

            shouldThrow<LimitExceededException> { LimitedInputStream(streamOf(10), 9).read(ByteArray(10)) }
        }
        should("count skipped bytes toward the limit") {
            val limited = LimitedInputStream(streamOf(10), 5)

            limited.skip(3) shouldBe 3L
            limited.skip(2) shouldBe 2L
            shouldThrow<LimitExceededException> { limited.skip(1) }
        }
        should("skip through one buffer") {
            val recording = RecordingStream(streamOf(100))
            val limited = LimitedInputStream(recording, 100)

            limited.skip(10) shouldBe 10L
            limited.skip(10) shouldBe 10L
            recording.arrays.distinct().size shouldBe 1
        }
        should("skip nothing for a non-positive count and at the end of the stream") {
            val limited = LimitedInputStream(streamOf(2), 5)

            limited.skip(0) shouldBe 0L
            limited.skip(-4) shouldBe 0L
            limited.skip(10) shouldBe 2L
            limited.skip(10) shouldBe 0L
        }
        should("end a limited empty stream without throwing") {
            LimitedInputStream(streamOf(0), 0).read(ByteArray(4)) shouldBe -1
        }
    },
)
