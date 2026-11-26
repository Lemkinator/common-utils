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

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import java.time.format.DateTimeParseException

/**
 * Formats [value] as ISO-8601 text with its UTC offset, e.g. `2026-10-27T14:30:00+01:00`.
 * A non-zero nanosecond value adds a fraction, e.g. `2026-10-27T14:30:00.123456789+01:00`.
 */
public fun formatOffsetStamp(value: OffsetDateTime): String = ISO_OFFSET_DATE_TIME.format(value)

/**
 * Parses ISO-8601 text with a UTC offset, the format of [formatOffsetStamp].
 * @throws DateTimeParseException if [text] is not such a stamp.
 */
public fun parseOffsetStamp(text: String): OffsetDateTime = OffsetDateTime.parse(text, ISO_OFFSET_DATE_TIME)
