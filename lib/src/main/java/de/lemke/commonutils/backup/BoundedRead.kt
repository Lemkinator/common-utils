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
internal fun InputStream.readAtMost(limit: Long): ByteArray? {
    val blocks = ArrayList<ByteArray>()
    val size = consumeAtMost(limit) { buffer, read -> blocks += buffer.copyOf(read) }
    return size?.let { joined(blocks, it.toInt()) }
}

/** Counts and drops the rest of this stream, or returns null once it passes [limit] bytes; it holds one buffer. */
internal fun InputStream.dropAtMost(limit: Long): Long? = consumeAtMost(limit) { _, _ -> }

private fun joined(
    blocks: List<ByteArray>,
    size: Int,
): ByteArray {
    val joined = ByteArray(size)
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
    while (total <= limit) {
        val request = if (limit - total < BUFFER_SIZE) (limit - total + 1).toInt() else BUFFER_SIZE
        val read = read(buffer, 0, request)
        if (read < 0) return total
        total += read
        onBlock(buffer, read)
    }
    return null
}

/** Thrown by [LimitedInputStream] on the first byte past its limit. */
internal class LimitExceededException : IOException("The stream passes its size limit")

/** Passes through at most [limit] bytes of [input] and throws [LimitExceededException] on the first byte after them. */
internal class LimitedInputStream(input: InputStream, private var limit: Long) : FilterInputStream(input) {
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
}
