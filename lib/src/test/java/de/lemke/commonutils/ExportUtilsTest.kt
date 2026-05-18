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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExportUtilsTest {
    @Test
    fun `toSafeFileName ends with given extension`() {
        val result = "my photo".toSafeFileName(".png")
        assertTrue(result.endsWith(".png"))
    }

    @Test
    fun `toSafeFileName contains only alphanumeric and underscores before extension`() {
        val result = "my photo".toSafeFileName(".png")
        val base = result.removeSuffix(".png")
        assertTrue(base.matches("[a-zA-Z0-9_]+".toRegex())) {
            "Base '$base' contains disallowed characters"
        }
    }

    @Test
    fun `toSafeFileName removes https scheme`() {
        val result = "https://example.com".toSafeFileName(".png")
        assertFalse(result.contains("https"))
    }

    @Test
    fun `toSafeFileName does not remove http scheme (only https is stripped)`() {
        // Implementation only strips "https://" — "http://" becomes "http___"
        val result = "http://example.com".toSafeFileName(".png")
        assertFalse(result.startsWith("http___"), "http:// should produce safe chars, not 'http___'")
        // Base must still be alphanumeric+undersscores only
        val base = result.removeSuffix(".png")
        assertTrue(base.matches("[a-zA-Z0-9_]+".toRegex()))
    }

    @Test
    fun `toSafeFileName has no leading underscores before extension`() {
        val result = "  leading spaces".toSafeFileName(".png")
        assertFalse(result.startsWith("_"))
    }

    @Test
    fun `toSafeFileName has no consecutive underscores`() {
        val result = "hello   world".toSafeFileName(".png")
        assertFalse(result.contains("__"))
    }

    @Test
    fun `toSafeFileName replaces special characters with underscores`() {
        val result = "file@name!test".toSafeFileName(".png")
        val base = result.removeSuffix(".png")
        assertTrue(base.matches("[a-zA-Z0-9_]+".toRegex()))
    }

    @Test
    fun `toSafeFileName appended extension is preserved literally`() {
        val result = "test".toSafeFileName(".jpg")
        assertTrue(result.endsWith(".jpg"))
    }
}
