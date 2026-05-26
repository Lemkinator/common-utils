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
package de.lemke.commonutils

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class SaveLocationTest : ShouldSpec(
    {
        context("fromStringOrDefault") {
            should("return CUSTOM for CUSTOM string") {
                SaveLocation.fromStringOrDefault("CUSTOM") shouldBe SaveLocation.CUSTOM
            }
            should("return DOWNLOADS for DOWNLOADS string") {
                SaveLocation.fromStringOrDefault("DOWNLOADS") shouldBe SaveLocation.DOWNLOADS
            }
            should("return PICTURES for PICTURES string") {
                SaveLocation.fromStringOrDefault("PICTURES") shouldBe SaveLocation.PICTURES
            }
            should("return DCIM for DCIM string") {
                SaveLocation.fromStringOrDefault("DCIM") shouldBe SaveLocation.DCIM
            }
            should("return default for null") {
                SaveLocation.fromStringOrDefault(null) shouldBe SaveLocation.default
            }
            should("return default for unknown string") {
                SaveLocation.fromStringOrDefault("UNKNOWN") shouldBe SaveLocation.default
            }
            should("be case-sensitive") {
                SaveLocation.fromStringOrDefault("custom") shouldBe SaveLocation.default
            }
        }
        context("entryValues") {
            should("contain all enum names in order") {
                SaveLocation.entryValues.toList() shouldContainExactly
                    listOf("CUSTOM", "DOWNLOADS", "PICTURES", "DCIM")
            }
        }
        context("default") {
            should("be CUSTOM") {
                SaveLocation.default shouldBe SaveLocation.CUSTOM
            }
        }
    },
)
