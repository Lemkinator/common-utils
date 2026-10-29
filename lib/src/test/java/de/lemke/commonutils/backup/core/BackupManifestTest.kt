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
import kotlinx.serialization.SerializationException

class BackupManifestTest : ShouldSpec(
    {
        val manifest =
            BackupManifest(
                kind = "backup",
                format = 1,
                app = "oneurl",
                appVersionCode = 1234,
                createdAt = "2026-10-27T14:30:00+01:00",
                sections = listOf(SectionInfo("urls", 1, 87)),
            )

        should("encode the manifest field names of the spec") {
            backupJson.encodeToString(BackupManifest.serializer(), manifest) shouldBe
                """{"kind":"backup","format":1,"app":"oneurl","appVersionCode":1234,""" +
                """"createdAt":"2026-10-27T14:30:00+01:00","sections":[{"id":"urls","version":1,"entries":87}]}"""
        }
        should("decode a manifest with an unknown key") {
            val text =
                """{"kind":"backup","format":1,"app":"oneurl","appVersionCode":1234,"extra":true,""" +
                    """"createdAt":"2026-10-27T14:30:00+01:00","sections":[{"id":"urls","version":1,"entries":87,"more":1}]}"""
            backupJson.decodeFromString(BackupManifest.serializer(), text) shouldBe manifest
        }
        should("refuse to decode a manifest without its required fields") {
            shouldThrow<SerializationException> { backupJson.decodeFromString(BackupManifest.serializer(), """{"kind":"backup"}""") }
        }
        should("refuse to decode a section info without its required fields") {
            shouldThrow<SerializationException> { backupJson.decodeFromString(SectionInfo.serializer(), """{"id":"urls"}""") }
        }
        should("name the manifest entry and a section entry") {
            MANIFEST_ENTRY shouldBe "manifest.json"
            sectionEntryName("urls") shouldBe "sections/urls.json"
        }
    },
)
