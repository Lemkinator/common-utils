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
import java.io.InputStream

private const val BUFFER_SIZE = 8 * 1024

/** Reads the rest of this stream, or returns null once it passes [limit] bytes; it never holds more than [limit] plus one buffer. */
internal fun InputStream.readAtMost(limit: Long): ByteArray? {
    val bytes = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    var total = 0L
    var read = read(buffer)
    while (read >= 0 && total <= limit) {
        total += read
        bytes.write(buffer, 0, read)
        read = read(buffer)
    }
    return bytes.toByteArray().takeIf { total <= limit }
}
