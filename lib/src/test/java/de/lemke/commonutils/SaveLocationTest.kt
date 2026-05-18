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

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SaveLocationTest {
    // region fromStringOrDefault

    @Test
    fun `fromStringOrDefault returns CUSTOM for CUSTOM string`() {
        assertEquals(SaveLocation.CUSTOM, SaveLocation.fromStringOrDefault("CUSTOM"))
    }

    @Test
    fun `fromStringOrDefault returns DOWNLOADS for DOWNLOADS string`() {
        assertEquals(SaveLocation.DOWNLOADS, SaveLocation.fromStringOrDefault("DOWNLOADS"))
    }

    @Test
    fun `fromStringOrDefault returns PICTURES for PICTURES string`() {
        assertEquals(SaveLocation.PICTURES, SaveLocation.fromStringOrDefault("PICTURES"))
    }

    @Test
    fun `fromStringOrDefault returns DCIM for DCIM string`() {
        assertEquals(SaveLocation.DCIM, SaveLocation.fromStringOrDefault("DCIM"))
    }

    @Test
    fun `fromStringOrDefault returns default for null`() {
        assertEquals(SaveLocation.default, SaveLocation.fromStringOrDefault(null))
    }

    @Test
    fun `fromStringOrDefault returns default for unknown string`() {
        assertEquals(SaveLocation.default, SaveLocation.fromStringOrDefault("UNKNOWN"))
    }

    @Test
    fun `fromStringOrDefault is case-sensitive`() {
        assertEquals(SaveLocation.default, SaveLocation.fromStringOrDefault("custom"))
    }

    // endregion

    // region entryValues

    @Test
    fun `entryValues contains all enum names`() {
        assertArrayEquals(arrayOf("CUSTOM", "DOWNLOADS", "PICTURES", "DCIM"), SaveLocation.entryValues)
    }

    // endregion

    // region default

    @Test
    fun `default is CUSTOM`() {
        assertEquals(SaveLocation.CUSTOM, SaveLocation.default)
    }

    // endregion
}
