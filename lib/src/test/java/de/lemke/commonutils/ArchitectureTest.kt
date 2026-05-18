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

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse

class ArchitectureTest : ShouldSpec() {
    private val codeScope = Konsist.scopeFromProduction()

    init {
        should("ui activities not depend on widget internals") {
            codeScope.files
                .withPackage("de.lemke.commonutils.ui.activity..")
                .forEach { file ->
                    file.imports.any { it.name.contains(".widget.internal.") }.shouldBeFalse()
                }
        }
        should("data layer not depend on ui") {
            codeScope.files
                .withPackage("de.lemke.commonutils.data..")
                .forEach { file ->
                    file.imports.any { it.name.startsWith("de.lemke.commonutils.ui.") }.shouldBeFalse()
                }
        }
    }
}
