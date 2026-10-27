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

import de.lemke.commonutils.backup.core.toHex
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class HmacSha256BackupSignerTest : ShouldSpec(
    {
        should("match RFC 4231 test case 2") {
            val signer = HmacSha256BackupSigner("test", "Jefe".encodeToByteArray())

            signer.mac("what do ya want for nothing?".encodeToByteArray()).toHex() shouldBe
                "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"
        }
        should("reject an empty key") {
            shouldThrow<IllegalArgumentException> { HmacSha256BackupSigner("test", ByteArray(0)) }
        }
        should("copy the key") {
            val key = "Jefe".encodeToByteArray()
            val signer = HmacSha256BackupSigner("test", key)
            key.fill(0)

            signer.mac("what do ya want for nothing?".encodeToByteArray()).toHex() shouldBe
                "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"
        }
        should("hide the key in toString") {
            val text = HmacSha256BackupSigner("release", "secret-key".encodeToByteArray()).toString()

            text shouldBe "HmacSha256BackupSigner(keyId=release)"
            text shouldNotContain "secret"
        }
    },
)
