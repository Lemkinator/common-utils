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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

@Serializable
private data class Ranked(val name: String, val rank: Int = 0, val stamp: Int = 0)

private val byName: Comparator<Ranked> = compareBy { it.name }

class MergeRulesTest : ShouldSpec(
    {
        val newer = newerWins<Ranked, Int>({ it.stamp }, byName)
        val ranked = higherRankWins<Ranked, Int, Int>({ it.rank }, { it.stamp }, byName)

        should("newerWins takes the incoming entry when its stamp is greater") {
            val local = Ranked("local", stamp = 1)
            val incoming = Ranked("incoming", stamp = 2)
            newer(local, incoming) shouldBe incoming
        }
        should("newerWins keeps the local entry when its stamp is greater") {
            val local = Ranked("local", stamp = 2)
            val incoming = Ranked("incoming", stamp = 1)
            newer(local, incoming) shouldBe local
        }
        should("newerWins on an equal stamp picks the entry greater by order, whichever side is local") {
            val a = Ranked("a", stamp = 1)
            val b = Ranked("b", stamp = 1)
            newer(a, b) shouldBe b
            newer(b, a) shouldBe b
        }
        should("higherRankWins takes the higher rank even when it is older") {
            val local = Ranked("local", rank = 1, stamp = 9)
            val incoming = Ranked("incoming", rank = 2, stamp = 1)
            ranked(local, incoming) shouldBe incoming
            ranked(incoming, local) shouldBe incoming
        }
        should("higherRankWins falls back to the newer stamp on an equal rank") {
            val older = Ranked("z", rank = 1, stamp = 1)
            val newest = Ranked("a", rank = 1, stamp = 2)
            ranked(older, newest) shouldBe newest
            ranked(newest, older) shouldBe newest
        }
        should("higherRankWins on an equal rank and stamp picks by order, whichever side is local") {
            val a = Ranked("a", rank = 1, stamp = 1)
            val b = Ranked("b", rank = 1, stamp = 1)
            ranked(a, b) shouldBe b
            ranked(b, a) shouldBe b
        }
        should("both helpers return local when order reports equal entries") {
            val local = Ranked("same", stamp = 1)
            val incoming = Ranked("same", stamp = 1)
            (newer(local, incoming) === local) shouldBe true
            (ranked(local, incoming) === local) shouldBe true
        }
        should("encodedOrder orders entries by their encoded JSON text") {
            val order = encodedOrder(Ranked.serializer())
            order.compare(Ranked("a", rank = 2), Ranked("b", rank = 1)) shouldBe -1
            order.compare(Ranked("b"), Ranked("a")) shouldBe 1
            order.compare(Ranked("a"), Ranked("a")) shouldBe 0
        }
    },
)
