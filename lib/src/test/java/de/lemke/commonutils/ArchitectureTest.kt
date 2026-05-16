/*
 * Copyright 2024-2025 Leonard Lemke
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
import org.junit.jupiter.api.Test

class ArchitectureTest {
    private val scope = Konsist.scopeFromProduction()

    @Test
    fun `ui activities do not depend on widget internals`() {
        scope.files
            .withPackage("de.lemke.commonutils.ui.activity..")
            .forEach { file ->
                assert(file.imports.none { it.name.contains(".widget.internal.") }) {
                    "${file.name} imports widget internals"
                }
            }
    }

    @Test
    fun `data layer does not depend on ui`() {
        scope.files
            .withPackage("de.lemke.commonutils.data..")
            .forEach { file ->
                assert(file.imports.none { it.name.startsWith("de.lemke.commonutils.ui.") }) {
                    "${file.name} in data layer imports UI"
                }
            }
    }
}
