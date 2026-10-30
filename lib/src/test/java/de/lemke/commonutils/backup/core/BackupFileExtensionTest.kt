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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class BackupFileExtensionTest : ShouldSpec(
    {
        should("hold a dot and lowercase letters or digits") {
            BackupFileExtension(".oneurl").value shouldBe ".oneurl"
            BackupFileExtension(".sudoku").toString() shouldBe ".sudoku"
            BackupFileExtension(".v2").value shouldBe ".v2"
        }

        listOf("", ".", "zip", ".Zip", ".a.b", ".a b", "..zip", ".zip.", ".ü").forEach { invalid ->
            should("refuse '$invalid'") {
                shouldThrow<IllegalArgumentException> { BackupFileExtension(invalid) }.message shouldBe
                    "A backup file extension is a dot and lowercase letters or digits, was '$invalid'"
            }
        }
    },
)
