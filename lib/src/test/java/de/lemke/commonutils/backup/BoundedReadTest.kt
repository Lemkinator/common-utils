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

private fun streamOf(size: Int) = ByteArrayInputStream(ByteArray(size) { it.toByte() })

class BoundedReadTest : ShouldSpec(
    {
        should("read a stream of exactly the limit and return null for one byte more") {
            streamOf(20_000).readAtMost(20_000)?.size shouldBe 20_000
            streamOf(20_001).readAtMost(20_000) shouldBe null
        }
        should("count a stream of exactly the limit and return null for one byte more") {
            streamOf(20_000).dropAtMost(20_000) shouldBe 20_000L
            streamOf(20_001).dropAtMost(20_000) shouldBe null
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
        should("end a limited empty stream without throwing") {
            LimitedInputStream(streamOf(0), 0).read(ByteArray(4)) shouldBe -1
        }
    },
)
