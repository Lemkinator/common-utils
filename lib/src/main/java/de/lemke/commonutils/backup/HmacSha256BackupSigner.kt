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

import de.lemke.commonutils.backup.core.BackupSigner
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val ALGORITHM = "HmacSHA256"

/**
 * The [BackupSigner] over HMAC-SHA256. An app opts in to signing with
 * `@Provides fun signer(): BackupSigner = HmacSha256BackupSigner(keyId, key)` in its own Hilt module.
 *
 * The key is copied and never printed. A file signed with another [keyId] or key is refused as changed.
 */
public class HmacSha256BackupSigner(
    override val keyId: String,
    key: ByteArray,
) : BackupSigner {
    private val key: ByteArray = key.copyOf()

    init {
        require(key.isNotEmpty()) { "The signing key must not be empty" }
    }

    override fun mac(parts: Sequence<ByteArray>): ByteArray =
        Mac.getInstance(ALGORITHM).run {
            init(SecretKeySpec(key, ALGORITHM))
            parts.forEach { update(it) }
            doFinal()
        }

    override fun toString(): String = "HmacSha256BackupSigner(keyId=$keyId)"
}
