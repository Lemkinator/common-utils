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
package de.lemke.commonutils.ui.utils

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class BrowsableUrlTest : ShouldSpec(
    {
        context("toBrowsableUrl rewrites a git-only repository URL to its web page") {
            listOf(
                "git@github.com:sfuhrm/sudoku.git",
                "scm:git:git@github.com:sfuhrm/sudoku.git",
                "git://github.com/sfuhrm/sudoku.git",
                "ssh://git@github.com:22/sfuhrm/sudoku.git",
                "scm:git:ssh://git@github.com/sfuhrm/sudoku",
                "git+ssh://git@github.com/sfuhrm/sudoku/",
            ).forEach { url -> should(url) { url.toBrowsableUrl() shouldBe "https://github.com/sfuhrm/sudoku" } }
        }

        should("toBrowsableUrl rewrites an ssh URL without a user on another host") {
            "ssh://gitlab.example.com/group/repo.git".toBrowsableUrl() shouldBe "https://gitlab.example.com/group/repo"
        }

        should("toBrowsableUrl drops the scm:git: prefix of a web URL") {
            "scm:git:https://github.com/sfuhrm/sudoku".toBrowsableUrl() shouldBe "https://github.com/sfuhrm/sudoku"
        }

        context("toBrowsableUrl keeps any other URL") {
            listOf(
                "https://github.com/sfuhrm/sudoku",
                "https://github.com/sfuhrm/sudoku.git",
                "mailto:dev@example.com",
                "",
            ).forEach { url -> should("keep '$url'") { url.toBrowsableUrl() shouldBe url } }
        }
    },
)
