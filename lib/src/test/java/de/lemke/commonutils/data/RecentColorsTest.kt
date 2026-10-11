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
package de.lemke.commonutils.data

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class RecentColorsTest : ShouldSpec() {
    init {
        context("withRecentColor") {
            should("put a new color first") {
                listOf(1, 2).withRecentColor(3) shouldBe listOf(3, 1, 2)
            }
            should("move a color it already holds to the front without duplicating it") {
                listOf(1, 2, 3).withRecentColor(2) shouldBe listOf(2, 1, 3)
            }
            should("cap the list at six colors by default") {
                listOf(1, 2, 3, 4, 5, 6).withRecentColor(7) shouldBe listOf(7, 1, 2, 3, 4, 5)
            }
            should("cap the list at the given maximum") {
                listOf(1, 2, 3).withRecentColor(4, max = 2) shouldBe listOf(4, 1)
            }
            should("start an empty list with the color") {
                emptyList<Int>().withRecentColor(5) shouldBe listOf(5)
            }
        }
    }
}
