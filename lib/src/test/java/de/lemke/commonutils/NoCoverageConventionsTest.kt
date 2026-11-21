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
import com.lemonappdev.konsist.api.container.KoScope
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

private const val NO_COVERAGE = "NoCoverage"

private val GETTER_MARK = Regex("@(?:\\w+\\.)*$NO_COVERAGE\\b")

/** Names every declaration that carries `@NoCoverage` and is not an `inline` function. */
private fun KoScope.misplacedNoCoverage(): List<String> =
    buildList {
        functions(includeNested = true, includeLocal = true)
            .filter { it.hasAnnotationWithName(NO_COVERAGE) && !it.hasInlineModifier }
            .forEach { add("function ${it.name}") }
        classes().filter { it.hasAnnotationWithName(NO_COVERAGE) }.forEach { add("class ${it.name}") }
        interfaces().filter { it.hasAnnotationWithName(NO_COVERAGE) }.forEach { add("interface ${it.name}") }
        objects().filter { it.hasAnnotationWithName(NO_COVERAGE) }.forEach { add("object ${it.name}") }
        properties(includeNested = true)
            .filter {
                it.hasAnnotationWithName(NO_COVERAGE) ||
                    it.getter?.text?.let(GETTER_MARK::containsMatchIn) == true
            }.forEach { add("property ${it.name}") }
    }.sorted()

class NoCoverageConventionsTest : ShouldSpec() {
    init {
        should("keep @NoCoverage on inline functions only") {
            Konsist.scopeFromProduction().misplacedNoCoverage().shouldBeEmpty()
        }
        should("see the annotated inline functions of both modules") {
            val annotated =
                Konsist
                    .scopeFromProduction()
                    .functions()
                    .filter { it.hasAnnotationWithName(NO_COVERAGE) }
                    .map { it.name }
                    .toSet()

            annotated.containsAll(listOf("restoreSearchAndActionMode", "addOnBackLogic")) shouldBe true
        }
        should("accept @NoCoverage on an inline function") {
            val source =
                """
                @NoCoverage
                inline fun <T> run(block: () -> T): T = block()
                """.trimIndent()

            withSourceScope(source) { scope -> scope.misplacedNoCoverage() } shouldBe emptyList()
        }
        should("report @NoCoverage on a function that is not inline") {
            val source =
                """
                @NoCoverage
                fun plain() = Unit
                """.trimIndent()

            withSourceScope(source) { scope -> scope.misplacedNoCoverage() } shouldBe listOf("function plain")
        }
        should("report a qualified @NoCoverage on a property getter") {
            val source =
                """
                val answer: Int
                    @de.lemke.commonutils.NoCoverage
                    get() = 42
                """.trimIndent()

            withSourceScope(source) { scope -> scope.misplacedNoCoverage() } shouldBe listOf("property answer")
        }
        should("report @NoCoverage on a class, an object, an interface and a property getter") {
            val source =
                """
                @NoCoverage
                class Widget
                @NoCoverage
                object Registry
                @NoCoverage
                interface Port
                val answer: Int
                    @NoCoverage
                    get() = 42
                """.trimIndent()

            withSourceScope(source) { scope -> scope.misplacedNoCoverage() } shouldBe
                listOf("class Widget", "interface Port", "object Registry", "property answer")
        }
    }
}
