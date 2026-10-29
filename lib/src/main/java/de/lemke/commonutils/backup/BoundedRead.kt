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

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

private const val BUFFER_SIZE = 8 * 1024
private const val BYTE_MASK = 0xFF

/**
 * Reads the rest of this stream, or returns null once it passes [limit] bytes. It requests at most [limit] + 1 bytes in
 * total and keeps them as exact-size blocks that it joins once, so no buffer outgrows the bytes read.
 */
internal fun InputStream.readAtMost(limit: Long): ByteArray? = readBlocks(limit)?.let(::joined)

/** The rest of this stream as blocks of [BUFFER_SIZE] bytes, the last one shorter; null once it passes [limit] bytes. */
internal fun InputStream.readBlocks(limit: Long): List<ByteArray>? {
    val blocks = ArrayList<ByteArray>()
    return blocks.takeIf { consumeAtMost(limit) { buffer, filled -> blocks += buffer.copyOf(filled) } != null }
}

/** Counts and drops the rest of this stream, or returns null once it passes [limit] bytes; it holds one buffer. */
internal fun InputStream.dropAtMost(limit: Long): Long? = consumeAtMost(limit) { _, _ -> }

private fun joined(blocks: List<ByteArray>): ByteArray {
    val joined = ByteArray(blocks.sumOf { it.size })
    var offset = 0
    blocks.forEach {
        it.copyInto(joined, offset)
        offset += it.size
    }
    return joined
}

private inline fun InputStream.consumeAtMost(
    limit: Long,
    onBlock: (buffer: ByteArray, read: Int) -> Unit,
): Long? {
    val buffer = ByteArray(BUFFER_SIZE)
    var total = 0L
    while (true) {
        val request = if (limit - total < BUFFER_SIZE) (limit - total + 1).toInt() else BUFFER_SIZE
        val filled = fill(buffer, request)
        total += filled
        if (total > limit) return null
        if (filled > 0) onBlock(buffer, filled)
        if (filled < request) return total
    }
}

/** Reads until [request] bytes are in [buffer] or the stream ends; a short read does not end a block. */
private fun InputStream.fill(
    buffer: ByteArray,
    request: Int,
): Int {
    var filled = 0
    while (filled < request) {
        val read = read(buffer, filled, request - filled)
        if (read < 0) break
        filled += read
    }
    return filled
}

/** Thrown by [LimitedInputStream] on the first byte past its limit. */
internal class LimitExceededException : IOException("The stream passes its size limit")

/** Passes through at most [limit] bytes of [input] and throws [LimitExceededException] on the first byte after them. */
internal class LimitedInputStream(input: InputStream, private var limit: Long) : FilterInputStream(input) {
    private val skipBuffer = ByteArray(BUFFER_SIZE)

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and BYTE_MASK
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        val read = super.read(b, off, len)
        if (read > 0) limit -= read
        if (limit < 0) throw LimitExceededException()
        return read
    }

    override fun skip(n: Long): Long {
        if (n <= 0) return 0
        val skipped = read(skipBuffer, 0, minOf(n, BUFFER_SIZE.toLong()).toInt())
        return maxOf(skipped, 0).toLong()
    }
}
