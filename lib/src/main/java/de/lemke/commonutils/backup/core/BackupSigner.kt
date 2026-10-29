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

/** Signs the bytes of a backup. The platform layer implements [mac] as HMAC-SHA256; the core stays free of `javax.crypto`. */
public interface BackupSigner {
    /** Names the key, so a file signed with another key is refused by name. */
    public val keyId: String

    /** HMAC-SHA256 of [parts] taken as one byte string, 32 bytes. An implementation consumes the parts one by one. */
    public fun mac(parts: Sequence<ByteArray>): ByteArray
}

/** HMAC-SHA256 of [payload], 32 bytes; [BackupSigner.mac] of one part. */
public fun BackupSigner.mac(payload: ByteArray): ByteArray = mac(sequenceOf(payload))
