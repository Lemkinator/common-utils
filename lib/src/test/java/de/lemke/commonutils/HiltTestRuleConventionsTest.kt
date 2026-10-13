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
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

private const val FIXTURE_IMPORT = "import de.lemke.commonutils.HiltTestRule"
private const val NO_RULE = "declares no single"
private const val NOT_OUTERMOST = "is not the outermost rule"
private const val RULE_FUNCTION = "declares a @Rule function"
private const val RAW_RULE = "uses HiltAndroidRule or RuleChain"
private const val NO_CLOSE_RULE = "declares no `@get:Rule val closeTestResources"

private fun hiltTest(
    body: String,
    imports: String = FIXTURE_IMPORT,
    pkg: String = "demo",
) = """
    package $pkg

    import dagger.hilt.android.testing.HiltAndroidTest
    import org.junit.Rule
    $imports

    @HiltAndroidTest
    class SampleTest {
    $body
    }
    """.trimIndent()

private fun plainTest(
    body: String,
    annotation: String = "@Config(application = HiltTestApplication::class, sdk = [36])",
    imports: String = FIXTURE_IMPORT,
    pkg: String = "demo",
) = """
    package $pkg

    import dagger.hilt.android.testing.HiltTestApplication
    import org.junit.Rule
    import org.robolectric.annotation.Config
    $imports

    $annotation
    class PlainTest {
    $body
    }
    """.trimIndent()

private fun <T> withScope(
    sources: Map<String, String>,
    block: (KoScope) -> T,
): T {
    val dir = createTempDirectory(Path("build", "tmp").toAbsolutePath(), "hilt-rule")
    try {
        sources.forEach { (name, text) -> dir.resolve(name).writeText(text) }
        return block(Konsist.scopeFromExternalDirectories(listOf(dir.toString())))
    } finally {
        dir.toFile().deleteRecursively()
    }
}

private fun KoScope.problems(requireClose: Boolean = false): String? =
    runCatching { assertHiltTestRuleConventions(requireClose) }.exceptionOrNull()?.message

class HiltTestRuleConventionsTest : ShouldSpec() {
    private val outermost = "    @get:Rule(order = 0)\n    val hiltRule = HiltTestRule(this)"

    init {
        should("pass a test that declares the rule as its only rule with order 0") {
            val body = "$outermost\n\n    lateinit var thing: Thing"
            withScope(mapOf("Ok.kt" to hiltTest(body))) { it.problems() shouldBe null }
        }
        should("pass a test in a file without a package") {
            val source = hiltTest(outermost).removePrefix("package demo\n\n")
            withScope(mapOf("Unpackaged.kt" to source)) { it.problems() shouldBe null }
        }
        should("pass a rule ordered before the other rules") {
            val body = "$outermost\n\n    @get:Rule(order = 1)\n    val other = Other()\n\n    @get:Rule(2)\n    val another = Other()"
            withScope(mapOf("Ok.kt" to hiltTest(body))) { it.problems() shouldBe null }
        }
        should("pass the rule constructed by named argument, with a declared type or by qualified name") {
            val named = "    @get:Rule(order = 0)\n    val hiltRule = HiltTestRule(testInstance = this)"
            val typed = "    @get:Rule(order = 0)\n    val hiltRule: HiltTestRule = HiltTestRule(this)"
            val qualified = "    @get:Rule(order = 0)\n    val hiltRule = de.lemke.commonutils.HiltTestRule(this)"
            val sources =
                mapOf(
                    "Named.kt" to hiltTest(named),
                    "Typed.kt" to hiltTest(typed),
                    "Qualified.kt" to hiltTest(qualified, imports = ""),
                )
            withScope(sources) { it.problems() shouldBe null }
        }
        should("resolve the rule through a wildcard import or the same package") {
            val sources =
                mapOf(
                    "Wildcard.kt" to hiltTest(outermost, imports = "import de.lemke.commonutils.*"),
                    "SamePackage.kt" to hiltTest(outermost, imports = "", pkg = "de.lemke.commonutils"),
                )
            withScope(sources) { it.problems() shouldBe null }
        }
        should("ignore a class without HiltAndroidTest") {
            val source = "package demo\n\nclass Plain {\n    @get:Rule\n    val a = Other()\n}\n"
            withScope(mapOf("Plain.kt" to source)) { it.problems() shouldBe null }
        }
        should("report a test without the rule") {
            val message = withScope(mapOf("None.kt" to hiltTest("    val x = 1"))) { it.problems() }

            message!! shouldStartWith "1 problem with the HiltTestRule conventions:\n"
            message shouldContain "None.kt: SampleTest: $NO_RULE"
        }
        should("report a rule that another class of the same name provides") {
            val sources = mapOf("Other.kt" to hiltTest(outermost, imports = "import other.HiltTestRule"))
            withScope(sources) { it.problems()!! shouldContain NO_RULE }
        }
        should("report a rule constructed with another argument than this") {
            val body = "    @get:Rule(order = 0)\n    val hiltRule = HiltTestRule(other)"
            withScope(mapOf("Other.kt" to hiltTest(body))) { it.problems()!! shouldContain NO_RULE }
        }
        should("report two rules") {
            val body = "$outermost\n\n    @get:Rule(order = 1)\n    val second = HiltTestRule(this)"
            withScope(mapOf("Two.kt" to hiltTest(body))) { it.problems()!! shouldContain NO_RULE }
        }
        should("report a rule without an explicit order") {
            val body = "    @get:Rule\n    val hiltRule = HiltTestRule(this)"
            withScope(mapOf("NoOrder.kt" to hiltTest(body))) { it.problems()!! shouldContain NOT_OUTERMOST }
        }
        should("report a sole rule with an order above 0") {
            val body = "    @get:Rule(order = 1)\n    val hiltRule = HiltTestRule(this)"
            withScope(mapOf("Late.kt" to hiltTest(body))) { it.problems()!! shouldContain NOT_OUTERMOST }
        }
        should("report a rule that another rule precedes or ties with, or one without an explicit order follows") {
            val before =
                "    @get:Rule(order = 0)\n    val other = Other()\n\n    @get:Rule(order = 1)\n" +
                    "    val hiltRule = HiltTestRule(this)"
            val tie = "$outermost\n\n    @get:Rule(order = 0)\n    val other = Other()"
            val implicit = "$outermost\n\n    @get:Rule\n    val other = Other()"
            val sources = mapOf("Before.kt" to hiltTest(before), "Tie.kt" to hiltTest(tie), "Implicit.kt" to hiltTest(implicit))
            val message = withScope(sources) { it.problems() }

            message!! shouldStartWith "3 problems with the HiltTestRule conventions:\n"
            message.lines().count { NOT_OUTERMOST in it } shouldBe 3
        }
        should("report an order that is no integer literal") {
            val body = "    @get:Rule(order = FIRST)\n    val hiltRule = HiltTestRule(this)"
            withScope(mapOf("Constant.kt" to hiltTest(body))) { it.problems()!! shouldContain NOT_OUTERMOST }
        }
        should("report a @Rule function") {
            val body = "$outermost\n\n    @Rule\n    fun extra() = Other()"
            withScope(mapOf("Fun.kt" to hiltTest(body))) { it.problems()!! shouldContain RULE_FUNCTION }
        }
        should("report a raw HiltAndroidRule through its import") {
            val imports = "$FIXTURE_IMPORT\n    import dagger.hilt.android.testing.HiltAndroidRule"
            withScope(mapOf("Raw.kt" to hiltTest(outermost, imports = imports))) { it.problems()!! shouldContain RAW_RULE }
        }
        should("report a raw HiltAndroidRule by qualified type") {
            val body = "$outermost\n\n    @get:Rule(order = 1)\n    val raw: dagger.hilt.android.testing.HiltAndroidRule = Other()"
            withScope(mapOf("Raw.kt" to hiltTest(body))) { it.problems()!! shouldContain RAW_RULE }
        }
        should("report a RuleChain, aliased or not") {
            val chain = "$outermost\n\n    @get:Rule(order = 1)\n    val chain = RuleChain.outerRule(Other())"
            val aliased = "$outermost\n\n    @get:Rule(order = 1)\n    val chain = Chain.outerRule(Other())"
            val sources =
                mapOf(
                    "Chain.kt" to hiltTest(chain, imports = "$FIXTURE_IMPORT\n    import org.junit.rules.RuleChain"),
                    "Aliased.kt" to
                        hiltTest(aliased, imports = "$FIXTURE_IMPORT\n    import org.junit.rules.RuleChain as Chain"),
                    "Wildcard.kt" to
                        hiltTest(chain, imports = "$FIXTURE_IMPORT\n    import org.junit.rules.*"),
                    "Qualified.kt" to hiltTest(chain.replace("RuleChain.", "org.junit.rules.RuleChain.")),
                )
            val message = withScope(sources) { it.problems() }

            message!! shouldStartWith "4 problems with the HiltTestRule conventions:\n"
            message.lines().count { RAW_RULE in it } shouldBe 4
        }
        should("list every problem of a class on its own line, sorted by path") {
            val body = "    @Rule\n    fun extra() = Other()"
            val late = hiltTest(outermost.replace("order = 0", "order = 3"))
            val message = withScope(mapOf("B.kt" to hiltTest(body), "A.kt" to late)) { it.problems() }

            val lines = message!!.lines()
            lines.size shouldBe 4
            lines[1] shouldContain "A.kt: SampleTest: HiltTestRule $NOT_OUTERMOST"
            lines[2] shouldContain "B.kt: SampleTest: $RULE_FUNCTION"
            lines[3] shouldContain "B.kt: SampleTest: $NO_RULE"
        }
        should("leave a plain Hilt application test unchecked unless asked") {
            withScope(mapOf("Plain.kt" to plainTest("    val x = 1"))) { it.problems() shouldBe null }
        }
        should("report a plain Hilt application test without the close rule when asked") {
            val withOther = plainTest("    @get:Rule\n    val other = Other()")
            val sources = mapOf("None.kt" to plainTest("    val x = 1"), "Other.kt" to withOther)
            val message = withScope(sources) { it.problems(requireClose = true) }

            message!! shouldStartWith "2 problems with the HiltTestRule conventions:\n"
            message.lines().count { NO_CLOSE_RULE in it } shouldBe 2
        }
        should("report a close rule that is no rule property or calls another function") {
            val noAnnotation = plainTest("    val closeTestResources = HiltTestRule.closeResourcesRule()")
            val otherCall = plainTest("    @get:Rule\n    val closeTestResources = HiltTestRule.other()")
            val foreign = plainTest("    @get:Rule\n    val closeTestResources = HiltTestRule.closeResourcesRule()", imports = "")
            val sources = mapOf("NoAnnotation.kt" to noAnnotation, "OtherCall.kt" to otherCall, "Foreign.kt" to foreign)
            val message = withScope(sources) { it.problems(requireClose = true) }

            message!! shouldStartWith "3 problems with the HiltTestRule conventions:\n"
        }
        should("pass a plain Hilt application test with the close rule") {
            val body = "    @get:Rule\n    val closeTestResources = HiltTestRule.closeResourcesRule()"
            val qualified = "    @get:Rule\n    val closeTestResources = de.lemke.commonutils.HiltTestRule.closeResourcesRule()"
            val sources =
                mapOf(
                    "Imported.kt" to plainTest(body),
                    "Qualified.kt" to plainTest(qualified, imports = ""),
                    "SamePackage.kt" to plainTest(body, imports = "", pkg = "de.lemke.commonutils"),
                    "Wildcard.kt" to plainTest(body, imports = "import de.lemke.commonutils.*"),
                    "Named.kt" to
                        plainTest(
                            body,
                            annotation = "@Config(sdk = [36], application = dagger.hilt.android.testing.HiltTestApplication::class)",
                        ),
                )
            withScope(sources) { scope ->
                shouldNotThrowAny { scope.assertHiltTestRuleConventions(requireCloseRuleOnPlainHiltApplicationTests = true) }
            }
        }
        should("not require the close rule from tests on another application, without Config, or with HiltAndroidTest") {
            val other = plainTest("    val x = 1", annotation = "@Config(application = OtherApplication::class)")
            val noApplication = plainTest("    val x = 1", annotation = "@Config(sdk = [36])")
            val noConfig = plainTest("    val x = 1", annotation = "")
            val hilt =
                hiltTest(outermost, imports = "$FIXTURE_IMPORT\n    import org.robolectric.annotation.Config")
                    .replace("@HiltAndroidTest\n", "@HiltAndroidTest\n@Config(application = HiltTestApplication::class)\n")
            val sources = mapOf("Other.kt" to other, "NoApplication.kt" to noApplication, "NoConfig.kt" to noConfig, "Hilt.kt" to hilt)
            withScope(sources) { it.problems(requireClose = true) shouldBe null }
        }
    }
}
