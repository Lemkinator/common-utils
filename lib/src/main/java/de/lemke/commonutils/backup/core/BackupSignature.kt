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

import kotlinx.serialization.Serializable

internal const val SIGNATURE_ALG: String = "HMAC-SHA256"

private const val SECTIONS_PREFIX = "sections/"
private const val LENGTH_BYTES = 8
private const val BITS_PER_BYTE = 8
private const val HEX_RADIX = 16
private const val BYTE_MASK = 0xFF

@Serializable
internal data class SignatureFile(val alg: String, val keyId: String, val mac: String)

/**
 * The bytes a signature covers: the `manifest.json` entry and every section entry sorted by name, each as its UTF-8 name,
 * a 0x00 byte, its length as 8 bytes big-endian, then its bytes. The length framing stops bytes from moving between
 * entries unnoticed. `files/` entries and `signature.json` stay out.
 */
internal fun signedPayload(files: Map<String, ByteArray>): ByteArray {
    val parts =
        files.entries
            .filter { (name) -> name == MANIFEST_ENTRY || (name.startsWith(SECTIONS_PREFIX) && name.endsWith(".json")) }
            .sortedBy { it.key }
            .flatMap { (name, bytes) -> listOf(name.encodeToByteArray(), byteArrayOf(0), bigEndian(bytes.size.toLong()), bytes) }
    val payload = ByteArray(parts.sumOf { it.size })
    var offset = 0
    parts.forEach { part ->
        part.copyInto(payload, offset)
        offset += part.size
    }
    return payload
}

private fun bigEndian(value: Long): ByteArray =
    ByteArray(LENGTH_BYTES) { index -> (value shr (BITS_PER_BYTE * (LENGTH_BYTES - 1 - index))).toByte() }

internal fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }

internal fun constantTimeEquals(
    a: ByteArray,
    b: ByteArray,
): Boolean = a.size == b.size && a.indices.fold(0) { difference, index -> difference or (a[index].toInt() xor b[index].toInt()) } == 0

internal fun signatureBytes(
    files: Map<String, ByteArray>,
    signer: BackupSigner,
): ByteArray {
    val signature = SignatureFile(SIGNATURE_ALG, signer.keyId, signer.mac(signedPayload(files)).toHex())
    return backupJson.encodeToString(SignatureFile.serializer(), signature).encodeToByteArray()
}

/** [BackupRefusal.Tampered] unless `signature.json` parses and carries this signer's alg, key id and MAC. */
internal fun signatureRefusal(
    files: Map<String, ByteArray>,
    signer: BackupSigner,
): BackupRefusal? {
    val signature = files[SIGNATURE_ENTRY]?.let(::decodeSignature)
    val expected = signer.mac(signedPayload(files)).toHex().encodeToByteArray()
    val valid =
        signature != null &&
            signature.alg == SIGNATURE_ALG &&
            signature.keyId == signer.keyId &&
            constantTimeEquals(signature.mac.encodeToByteArray(), expected)
    return BackupRefusal.Tampered.takeUnless { valid }
}

private fun decodeSignature(bytes: ByteArray): SignatureFile? = bytes.decodeWithinLimits(SignatureFile.serializer())
