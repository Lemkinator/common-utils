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

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val BUFFER_SIZE = 8 * 1024
private const val BYTE_MASK = 0xFF

/** Reads the rest of this stream, or returns null once it passes [limit] bytes; it never holds more than [limit] plus one buffer. */
internal fun InputStream.readAtMost(limit: Long): ByteArray? =
    ByteArrayOutputStream().let { sink -> copyAtMost(limit, sink)?.let { sink.toByteArray() } }

/** Counts and drops the rest of this stream, or returns null once it passes [limit] bytes; it holds one buffer. */
internal fun InputStream.dropAtMost(limit: Long): Long? = copyAtMost(limit, null)

private fun InputStream.copyAtMost(
    limit: Long,
    sink: OutputStream?,
): Long? {
    val buffer = ByteArray(BUFFER_SIZE)
    var total = 0L
    var read = read(buffer)
    while (read >= 0 && total <= limit) {
        total += read
        sink?.write(buffer, 0, read)
        read = read(buffer)
    }
    return total.takeIf { it <= limit }
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
