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

import de.lemke.commonutils.ui.utils.urlEncode
import de.lemke.commonutils.ui.utils.urlEncodeAmpersand
import de.lemke.commonutils.ui.utils.withHttps
import de.lemke.commonutils.ui.utils.withoutHttps
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class URLUtilsTest : ShouldSpec(
    {
        context("withHttps") {
            should("prepend https to bare domain") {
                "example.com".withHttps() shouldBe "https://example.com"
            }
            should("leave https URL unchanged") {
                "https://example.com".withHttps() shouldBe "https://example.com"
            }
            should("leave http URL unchanged") {
                "http://example.com".withHttps() shouldBe "http://example.com"
            }
            should("prepend https to path without scheme") {
                "example.com/path".withHttps() shouldBe "https://example.com/path"
            }
        }
        context("withoutHttps") {
            should("remove https scheme") {
                "https://example.com".withoutHttps() shouldBe "example.com"
            }
            should("remove http scheme") {
                "http://example.com".withoutHttps() shouldBe "example.com"
            }
            should("remove trailing slash") {
                "https://example.com/".withoutHttps() shouldBe "example.com"
            }
            should("leave plain domain unchanged") {
                "example.com".withoutHttps() shouldBe "example.com"
            }
            should("strip https then http from doubly-prefixed input") {
                "https://http://example.com".withoutHttps() shouldBe "example.com"
            }
        }
        context("urlEncodeAmpersand") {
            should("replace single ampersand") {
                "a&b".urlEncodeAmpersand() shouldBe "a%26b"
            }
            should("replace multiple ampersands") {
                "a&b&c".urlEncodeAmpersand() shouldBe "a%26b%26c"
            }
            should("leave string without ampersand unchanged") {
                "hello".urlEncodeAmpersand() shouldBe "hello"
            }
        }
        context("urlEncode") {
            should("encode space as plus") {
                "hello world".urlEncode() shouldBe "hello+world"
            }
            should("encode special characters") {
                "a&b".urlEncode() shouldBe "a%26b"
            }
            should("leave alphanumeric unchanged") {
                "abc123".urlEncode() shouldBe "abc123"
            }
        }
    },
)
