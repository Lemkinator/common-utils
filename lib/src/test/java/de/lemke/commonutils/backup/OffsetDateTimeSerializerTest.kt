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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class OffsetDateTimeSerializerTest : ShouldSpec(
    {
        should("formatOffsetStamp keeps a positive offset") {
            formatOffsetStamp(OffsetDateTime.of(2026, 10, 27, 14, 30, 0, 0, ZoneOffset.ofHours(1))) shouldBe "2026-10-27T14:30:00+01:00"
        }
        should("formatOffsetStamp keeps a negative offset") {
            formatOffsetStamp(OffsetDateTime.of(2026, 10, 27, 14, 30, 59, 0, ZoneOffset.ofHoursMinutes(-5, -30))) shouldBe
                "2026-10-27T14:30:59-05:30"
        }
        should("formatOffsetStamp writes nanoseconds as a fraction") {
            formatOffsetStamp(OffsetDateTime.of(2026, 10, 27, 14, 30, 0, 123_456_789, ZoneOffset.ofHours(1))) shouldBe
                "2026-10-27T14:30:00.123456789+01:00"
        }
        should("formatOffsetStamp writes Z for UTC") {
            formatOffsetStamp(OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)) shouldBe "2026-01-02T03:04:05Z"
        }
        should("parseOffsetStamp reads a positive offset") {
            val parsed = parseOffsetStamp("2026-10-27T14:30:00+01:00")
            parsed.offset shouldBe ZoneOffset.ofHours(1)
            parsed.toEpochSecond() shouldBe 1_793_107_800L
        }
        should("parseOffsetStamp reads a negative offset") {
            parseOffsetStamp("2026-10-27T14:30:59-05:30") shouldBe
                OffsetDateTime.of(2026, 10, 27, 14, 30, 59, 0, ZoneOffset.ofHoursMinutes(-5, -30))
        }
        should("parseOffsetStamp reads nanoseconds") {
            parseOffsetStamp("2026-10-27T14:30:00.123456789+01:00") shouldBe
                OffsetDateTime.of(2026, 10, 27, 14, 30, 0, 123_456_789, ZoneOffset.ofHours(1))
        }
        should("parseOffsetStamp reads Z") {
            parseOffsetStamp("2026-01-02T03:04:05Z") shouldBe OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)
        }
        should("parseOffsetStamp rejects a broken stamp") {
            shouldThrow<DateTimeParseException> { parseOffsetStamp("2026-10-27 14:30") }
        }
        should("the serializer writes a JSON string with the offset") {
            Json.encodeToString(OffsetDateTimeSerializer, OffsetDateTime.of(2026, 10, 27, 14, 30, 0, 0, ZoneOffset.ofHours(1))) shouldBe
                "\"2026-10-27T14:30:00+01:00\""
        }
        should("the serializer reads each offset back") {
            Json.decodeFromString(OffsetDateTimeSerializer, "\"2026-10-27T14:30:00+01:00\"") shouldBe
                OffsetDateTime.of(2026, 10, 27, 14, 30, 0, 0, ZoneOffset.ofHours(1))
            Json.decodeFromString(OffsetDateTimeSerializer, "\"2026-10-27T14:30:59-05:30\"") shouldBe
                OffsetDateTime.of(2026, 10, 27, 14, 30, 59, 0, ZoneOffset.ofHoursMinutes(-5, -30))
            Json.decodeFromString(OffsetDateTimeSerializer, "\"2026-01-02T03:04:05Z\"") shouldBe
                OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)
        }
        should("the serializer round-trips nanoseconds") {
            val stamp = OffsetDateTime.of(2026, 10, 27, 14, 30, 0, 123_456_789, ZoneOffset.ofHours(1))
            val json = Json.encodeToString(OffsetDateTimeSerializer, stamp)
            json shouldBe "\"2026-10-27T14:30:00.123456789+01:00\""
            Json.decodeFromString(OffsetDateTimeSerializer, json) shouldBe stamp
        }
        should("the serializer turns a broken stamp into a SerializationException naming the text") {
            val e = shouldThrow<SerializationException> { Json.decodeFromString(OffsetDateTimeSerializer, "\"yesterday\"") }
            e.message shouldBe "Invalid ISO-8601 offset date-time: yesterday"
        }
    },
)
