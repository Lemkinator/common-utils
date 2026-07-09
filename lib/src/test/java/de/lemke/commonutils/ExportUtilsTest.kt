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

import de.lemke.commonutils.ui.utils.toSafeFileName
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldNotStartWith

class ExportUtilsTest : ShouldSpec(
    {
        should("toSafeFileName end with given extension") {
            "my photo".toSafeFileName(".png") shouldEndWith ".png"
        }
        should("toSafeFileName contain only alphanumeric and underscores before extension") {
            val result = "my photo".toSafeFileName(".png")
            result.removeSuffix(".png") shouldMatch "[a-zA-Z0-9_]+"
        }
        should("toSafeFileName remove https scheme") {
            "https://example.com".toSafeFileName(".png") shouldNotContain "https"
        }
        should("toSafeFileName not start with http___ after http scheme sanitization") {
            val result = "http://example.com".toSafeFileName(".png")
            result.shouldNotStartWith("http___")
            result.removeSuffix(".png") shouldMatch "[a-zA-Z0-9_]+"
        }
        should("toSafeFileName have no leading underscores before extension") {
            "  leading spaces".toSafeFileName(".png").startsWith("_").shouldBeFalse()
        }
        should("toSafeFileName have no consecutive underscores") {
            "hello   world".toSafeFileName(".png") shouldNotContain "__"
        }
        should("toSafeFileName replace special characters with underscores") {
            "file@name!test"
                .toSafeFileName(".png")
                .removeSuffix(".png") shouldMatch "[a-zA-Z0-9_]+"
        }
        should("toSafeFileName preserve appended extension literally") {
            "test".toSafeFileName(".jpg") shouldEndWith ".jpg"
        }
    },
)
