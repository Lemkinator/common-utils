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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class URLUtilsTest {
    // region withHttps

    @Test
    fun `withHttps prepends https to bare domain`() {
        assertEquals("https://example.com", "example.com".withHttps())
    }

    @Test
    fun `withHttps leaves https URL unchanged`() {
        assertEquals("https://example.com", "https://example.com".withHttps())
    }

    @Test
    fun `withHttps leaves http URL unchanged`() {
        assertEquals("http://example.com", "http://example.com".withHttps())
    }

    @Test
    fun `withHttps prepends https to path without scheme`() {
        assertEquals("https://example.com/path", "example.com/path".withHttps())
    }

    // endregion

    // region withoutHttps

    @Test
    fun `withoutHttps removes https scheme`() {
        assertEquals("example.com", "https://example.com".withoutHttps())
    }

    @Test
    fun `withoutHttps removes http scheme`() {
        assertEquals("example.com", "http://example.com".withoutHttps())
    }

    @Test
    fun `withoutHttps removes trailing slash`() {
        assertEquals("example.com", "https://example.com/".withoutHttps())
    }

    @Test
    fun `withoutHttps leaves plain domain unchanged`() {
        assertEquals("example.com", "example.com".withoutHttps())
    }

    @Test
    fun `withoutHttps strips https then http from doubly-prefixed input`() {
        // removePrefix("https://") → "http://example.com", then removePrefix("http://") → "example.com"
        assertEquals("example.com", "https://http://example.com".withoutHttps())
    }

    // endregion

    // region urlEncodeAmpersand

    @Test
    fun `urlEncodeAmpersand replaces single ampersand`() {
        assertEquals("a%26b", "a&b".urlEncodeAmpersand())
    }

    @Test
    fun `urlEncodeAmpersand replaces multiple ampersands`() {
        assertEquals("a%26b%26c", "a&b&c".urlEncodeAmpersand())
    }

    @Test
    fun `urlEncodeAmpersand leaves string without ampersand unchanged`() {
        assertEquals("hello", "hello".urlEncodeAmpersand())
    }

    // endregion

    // region urlEncode

    @Test
    fun `urlEncode encodes space as plus`() {
        assertEquals("hello+world", "hello world".urlEncode())
    }

    @Test
    fun `urlEncode encodes special characters`() {
        assertEquals("a%26b", "a&b".urlEncode())
    }

    @Test
    fun `urlEncode leaves alphanumeric unchanged`() {
        assertEquals("abc123", "abc123".urlEncode())
    }

    // endregion
}
