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

import com.lemonappdev.konsist.core.exception.KoAssertionFailedException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec

class BackupConventionsTest : ShouldSpec(
    {
        should("fail on an @Entity @Serializable class") {
            val source =
                "package demo\n\nimport androidx.room.Entity\nimport kotlinx.serialization.Serializable\n\n" +
                    "@Entity\n@Serializable\ndata class UrlDb(val shortURL: String)\n"

            withSourceScope(source) { scope -> shouldThrow<KoAssertionFailedException> { scope.assertBackupConventions() } }
        }
        should("pass on separate entity and DTO classes") {
            val source =
                "package demo\n\nimport androidx.room.Entity\nimport kotlinx.serialization.Serializable\n\n" +
                    "@Entity\ndata class UrlDb(val shortURL: String)\n\n@Serializable\ndata class UrlBackupEntry(val shortURL: String)\n"

            withSourceScope(source) { scope -> shouldNotThrowAny { scope.assertBackupConventions() } }
        }
    },
)
