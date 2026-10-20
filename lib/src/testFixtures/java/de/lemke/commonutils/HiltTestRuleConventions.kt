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

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.ext.list.withoutAnnotationOf
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.rules.RuleChain
import org.robolectric.annotation.Config

private val ruleQualifiedName = HiltTestRule::class.java.name
private val ruleName = ruleQualifiedName.substringAfterLast('.')
private val rulePackage = ruleQualifiedName.substringBeforeLast('.')
private val applicationQualifiedName = HiltTestApplication::class.java.name
private val applicationName = applicationQualifiedName.substringAfterLast('.')
private val rawRuleTypes = setOf(HiltAndroidRule::class.java.name, RuleChain::class.java.name)
private val identifierChain = Regex("""[A-Za-z_]\w*(\.[A-Za-z_]\w*)*""")
private val directConstruction =
    Regex("""^(${Regex.escape("$rulePackage.")})?${Regex.escape(ruleName)}\((testInstance\s*=\s*)?this\)$""")
private val closeRuleCall =
    Regex("""^(${Regex.escape("$rulePackage.")})?${Regex.escape(ruleName)}\.closeResourcesRule\(\)$""")

private const val MISSING_RULE = "declares no single `@get:Rule(order = 0) val hiltRule = HiltTestRule(this)`"
private const val NOT_OUTERMOST = "HiltTestRule is not the outermost rule: every other rule needs a higher explicit order"
private const val RULE_FUNCTION = "declares a @Rule function: declare a @get:Rule property instead"
private const val RAW_RULE = "uses HiltAndroidRule or RuleChain: HiltTestRule replaces both"
private const val MISSING_CLOSE_RULE =
    "runs on HiltTestApplication without @HiltAndroidTest and declares no " +
        "`@get:Rule val closeTestResources = HiltTestRule.closeResourcesRule()`"

/**
 * Fails if a test class in this scope breaks the [HiltTestRule] conventions. Konsist has no type resolution, so
 * the checks match names and imports.
 *
 * Every `@HiltAndroidTest` class must declare exactly one `@get:Rule(order = 0) val hiltRule = HiltTestRule(this)`
 * ordered before every other rule, and no raw `HiltAndroidRule`, no `RuleChain` and no `@Rule` function.
 *
 * With [requireCloseRuleOnPlainHiltApplicationTests], a class that runs on `HiltTestApplication` through
 * `@Config(application = ...)` without `@HiltAndroidTest` must also declare
 * `@get:Rule val closeTestResources = HiltTestRule.closeResourcesRule()`.
 *
 * Fails with one message that lists every problem as `projectPath: Class: reason`, one per line.
 */
public fun KoScope.assertHiltTestRuleConventions(requireCloseRuleOnPlainHiltApplicationTests: Boolean = false) {
    val plainClasses =
        if (requireCloseRuleOnPlainHiltApplicationTests) {
            classes()
                .withAnnotationOf(Config::class)
                .withoutAnnotationOf(HiltAndroidTest::class)
                .filter { it.runsOnHiltTestApplication() }
        } else {
            emptyList()
        }
    val hits =
        classes().withAnnotationOf(HiltAndroidTest::class).flatMap { koClass -> koClass.hiltRuleProblems().map(koClass::describe) } +
            plainClasses.filterNot { it.declaresCloseRule() }.map { it.describe(MISSING_CLOSE_RULE) }
    if (hits.isNotEmpty()) {
        val header = "${hits.size} ${if (hits.size == 1) "problem" else "problems"} with the HiltTestRule conventions:"
        throw AssertionError(hits.sorted().joinToString("\n", prefix = "$header\n"))
    }
}

private fun KoClassDeclaration.describe(problem: String): String = "${portableProjectPath(containingFile.projectPath)}: $name: $problem"

private fun KoClassDeclaration.hiltRuleProblems(): List<String> {
    val file = containingFile
    val properties = properties(includeNested = false)
    val hiltRule = properties.filter { it.isHiltRuleCandidate(file) }.singleOrNull()?.takeIf { it.constructsHiltRule(file) }
    val hiltOrder = hiltRule?.explicitRuleOrder()
    val otherOrders = properties.filter { it != hiltRule }.mapNotNull { it.ruleOrder() }
    val outermost =
        when {
            hiltOrder == null -> false
            otherOrders.isEmpty() -> hiltOrder == 0
            else -> otherOrders.all { hiltOrder < it }
        }
    val usesRawRule =
        file.hasImport { it.name == HiltAndroidRule::class.java.name } ||
            properties.any { property ->
                listOfNotNull(property.type?.text, property.value).any { text -> file.resolveReferences(text).any(rawRuleTypes::contains) }
            }
    return listOfNotNull(
        MISSING_RULE.takeIf { hiltRule == null },
        NOT_OUTERMOST.takeIf { hiltRule != null && !outermost },
        RULE_FUNCTION.takeIf { functions(includeNested = false).any { function -> function.hasAnnotation { it.name == "Rule" } } },
        RAW_RULE.takeIf { usesRawRule },
    )
}

private fun KoClassDeclaration.runsOnHiltTestApplication(): Boolean =
    annotations
        .filter { it.name.substringAfterLast('.') == Config::class.java.simpleName }
        .flatMap { it.arguments }
        .any { argument ->
            argument.name == "application" &&
                argument.value
                    ?.trim()
                    ?.removeSuffix("::class")
                    ?.trim() in setOf(applicationName, applicationQualifiedName)
        }

private fun KoClassDeclaration.declaresCloseRule(): Boolean {
    val file = containingFile
    return properties(includeNested = false).any { property ->
        val initializer = property.value?.trim()
        property.hasAnnotation { it.name == "Rule" } &&
            initializer != null &&
            closeRuleCall.matches(initializer) &&
            (initializer.startsWith(ruleQualifiedName) || file.resolvesToFixturePackage())
    }
}

private fun KoFileDeclaration.resolvesToFixturePackage(): Boolean =
    packagee?.name == rulePackage ||
        hasImport { it.name == ruleQualifiedName || (it.isWildcard && it.name.removeSuffix(".*") == rulePackage) }

private fun KoPropertyDeclaration.constructsHiltRule(file: KoFileDeclaration): Boolean {
    val initializer = value?.trim()
    return initializer != null &&
        directConstruction.matches(initializer) &&
        (initializer.startsWith(ruleQualifiedName) || file.resolvesToFixturePackage())
}

private fun KoPropertyDeclaration.isHiltRuleCandidate(file: KoFileDeclaration): Boolean {
    val typeText = type?.text
    return typeText == ruleQualifiedName || (typeText == ruleName && file.resolvesToFixturePackage()) || constructsHiltRule(file)
}

private fun KoPropertyDeclaration.explicitRuleOrder(): Int? =
    annotations
        .firstOrNull { it.name == "Rule" }
        ?.arguments
        ?.firstOrNull { it.name == "order" || it.name.isEmpty() }
        ?.value
        ?.trim()
        ?.toIntOrNull()

private fun KoPropertyDeclaration.ruleOrder(): Int? =
    if (hasAnnotation { it.name == "Rule" }) explicitRuleOrder() ?: Rule.DEFAULT_ORDER else null

private fun KoFileDeclaration.resolveReferences(text: String): Set<String> =
    identifierChain
        .findAll(text)
        .flatMap { match ->
            val segments = match.value.split('.')
            val head = segments.first()
            val heads =
                imports.filter { !it.isWildcard && (it.alias?.name ?: it.name.substringAfterLast('.')) == head }.map { it.name } +
                    imports.filter { it.isWildcard }.map { "${it.name.removeSuffix(".*")}.$head" } +
                    listOfNotNull(packagee?.name?.let { "$it.$head" }) +
                    head
            heads.flatMap { resolvedHead ->
                segments.indices.map { last -> (listOf(resolvedHead) + segments.subList(1, last + 1)).joinToString(".") }
            }
        }.toSet()
