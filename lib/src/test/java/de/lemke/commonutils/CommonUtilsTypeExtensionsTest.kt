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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class CommonUtilsTypeExtensionsTest : ShouldSpec() {
    private fun flagged(source: String): String =
        withSourceScope(source) { scope ->
            shouldThrow<AssertionError> { scope.assertNoCommonUtilsTypeExtensions() }.message.orEmpty()
        }

    private fun passes(source: String) =
        withSourceScope(source) { scope ->
            shouldNotThrowAny { scope.assertNoCommonUtilsTypeExtensions() }
        }

    init {
        should("list a private extension property with an explicit import as project path, line and name") {
            val source =
                "package demo\n\nimport de.lemke.commonutils.ui.utils.LaunchOutcome\n\n" +
                    "private val LaunchOutcome.label: String get() = \"x\"\n"

            withSourceScope(mapOf("Hits.kt" to source)) { scope, directoryName ->
                val error = shouldThrow<AssertionError> { scope.assertNoCommonUtilsTypeExtensions() }

                error.message shouldBe
                    "1 extension on a common-utils type is declared in the app:\n" +
                    "/lib/build/tmp/$directoryName/Hits.kt:5: LaunchOutcome.label"
            }
        }
        should("flag an extension function with an explicit import") {
            flagged(
                "package demo\n\nimport de.lemke.commonutils.ui.utils.LaunchOutcome\n\nfun LaunchOutcome.describe(): String = \"x\"\n",
            ) shouldContain ":5: LaunchOutcome.describe"
        }
        should("flag an extension through a wildcard import") {
            flagged(
                "package demo\n\nimport de.lemke.commonutils.ui.utils.*\n\ninternal fun LaunchOutcome.describe(): String = \"x\"\n",
            ) shouldContain ":5: LaunchOutcome.describe"
        }
        should("flag an extension through an aliased import") {
            flagged(
                "package demo\n\nimport de.lemke.commonutils.ui.utils.LaunchOutcome as Outcome\n\nfun Outcome.describe() = \"x\"\n",
            ) shouldContain ":5: Outcome.describe"
        }
        should("flag a fully qualified receiver") {
            flagged("package demo\n\nfun de.lemke.commonutils.ui.utils.LaunchOutcome.describe() = \"x\"\n") shouldContain
                ":3: de.lemke.commonutils.ui.utils.LaunchOutcome.describe"
        }
        should("flag a nullable, generic or nested library receiver") {
            val source =
                """
                package demo

                import de.lemke.commonutils.ui.utils.BitmapExport
                import de.lemke.commonutils.ui.utils.LaunchOutcome

                fun LaunchOutcome?.a() = 1

                fun <T> BitmapExport<T>.b() = 2

                val BitmapExport.Result.c get() = 3
                """.trimIndent()

            val message = flagged(source)

            message shouldContain "3 extensions on common-utils types are declared in the app:\n"
            message shouldContain ":6: LaunchOutcome.a"
            message shouldContain ":8: BitmapExport.b"
            message shouldContain ":10: BitmapExport.Result.c"
        }
        should("flag a member extension inside a class and an object") {
            val source =
                """
                package demo

                import de.lemke.commonutils.ui.utils.LaunchOutcome

                class Screen {
                    private fun LaunchOutcome.text() = "x"

                    object Holder {
                        val LaunchOutcome.code get() = 1
                    }
                }
                """.trimIndent()

            val message = flagged(source)

            message shouldContain ":6: LaunchOutcome.text"
            message shouldContain ":9: LaunchOutcome.code"
        }
        should("list several hits sorted by path and line") {
            val bad =
                "package demo\n\nimport de.lemke.commonutils.ui.utils.LaunchOutcome\n\n" +
                    "val LaunchOutcome.b get() = 1\n\nval LaunchOutcome.a get() = 2\n"
            val sources = mapOf("B.kt" to bad, "A.kt" to bad)

            withSourceScope(sources) { scope, directoryName ->
                val error = shouldThrow<AssertionError> { scope.assertNoCommonUtilsTypeExtensions() }

                val base = "/lib/build/tmp/$directoryName"
                error.message shouldBe
                    "4 extensions on common-utils types are declared in the app:\n" +
                    "$base/A.kt:5: LaunchOutcome.b\n$base/A.kt:7: LaunchOutcome.a\n" +
                    "$base/B.kt:5: LaunchOutcome.b\n$base/B.kt:7: LaunchOutcome.a"
            }
        }
        should("pass an extension on a type that is no common-utils type") {
            passes(
                """
                package demo

                import android.content.Context
                import de.lemke.commonutils.ui.utils.LaunchOutcome

                private fun Context.name() = "x"

                fun String.shout() = uppercase()

                val List<LaunchOutcome>.size2 get() = size * 2

                fun (() -> LaunchOutcome).call() = invoke()
                """.trimIndent(),
            )
        }
        should("pass an extension on a type of the app with a common-utils name") {
            withSourceScope(
                mapOf(
                    "Own.kt" to "package demo\n\nclass LaunchOutcome\n",
                    "Ext.kt" to "package demo\n\nimport de.lemke.commonutils.ui.utils.*\n\nfun LaunchOutcome.describe() = \"x\"\n",
                ),
            ) { scope, _ -> shouldNotThrowAny { scope.assertNoCommonUtilsTypeExtensions() } }
        }
        should("pass a name imported from another package even next to a common-utils wildcard import") {
            passes(
                "package demo\n\nimport de.lemke.commonutils.ui.utils.*\nimport other.LaunchOutcome\n\n" +
                    "fun LaunchOutcome.describe() = \"x\"\n",
            )
        }
        should("pass a default type next to a common-utils wildcard import") {
            passes("package demo\n\nimport de.lemke.commonutils.ui.utils.*\n\nfun String.shout() = uppercase()\n")
        }
        should("pass an ambiguous name when another wildcard import could supply it") {
            passes(
                "package demo\n\nimport android.content.*\nimport de.lemke.commonutils.ui.utils.*\n\nfun Context.name() = \"x\"\n",
            )
        }
        should("pass an extension in a file of the common-utils package") {
            passes(
                "package de.lemke.commonutils.ui.utils\n\nimport de.lemke.commonutils.ui.utils.LaunchOutcome\n\n" +
                    "val LaunchOutcome.label get() = \"x\"\n",
            )
        }
        should("pass a file without extensions") {
            passes(
                "package demo\n\nimport de.lemke.commonutils.ui.utils.LaunchOutcome\n\n" +
                    "class Screen {\n    fun show(outcome: LaunchOutcome) = Unit\n}\n",
            )
        }
    }
}
