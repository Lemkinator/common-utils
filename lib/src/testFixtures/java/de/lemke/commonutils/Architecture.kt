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

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.provider.KoParentProvider
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue

/**
 * Fails if this scope breaks the shared layering and ViewModel rules of the apps under [rootPackage]; each rule is
 * its own assertion, so a failure names the broken rule.
 *
 * - `data` does not import `ui`, and imports from `domain` only `domain.model`
 * - `domain` does not import `ui`
 * - `*UseCase` classes declare `operator fun invoke`
 * - `*ViewModel` classes extend `ViewModel` or `AndroidViewModel`, directly or through a base class in the scope
 * - ViewModel files expose state only: no `Channel`, `SharedFlow` or their operators
 * - `@HiltViewModel` classes have an `@Inject` primary constructor
 *
 * Konsist has no type resolution, so the event-stream rule matches names, not resolved types.
 */
fun KoScope.assertArchitecture(rootPackage: String) {
    files
        .withPackage("$rootPackage.data..")
        .assertFalse(testName = "data layer does not depend on ui") {
            it.hasImport { import -> import.name.startsWith("$rootPackage.ui.") }
        }
    files
        .withPackage("$rootPackage.data..")
        .assertFalse(testName = "data layer does not depend on domain use cases") {
            it.hasImport { import ->
                import.name.startsWith("$rootPackage.domain.") && !import.name.startsWith("$rootPackage.domain.model.")
            }
        }
    files
        .withPackage("$rootPackage.domain..")
        .assertFalse(testName = "domain layer does not depend on ui") {
            it.hasImport { import -> import.name.startsWith("$rootPackage.ui.") }
        }
    classes()
        .filter { it.name.endsWith("UseCase") }
        .assertTrue(testName = "use case classes declare operator fun invoke") { koClass ->
            koClass
                .functions(includeNested = false, includeLocal = false)
                .any { it.name == "invoke" && it.hasModifier(KoModifier.OPERATOR) }
        }
    classes()
        .filter { it.name.endsWith("ViewModel") }
        .assertTrue(testName = "classes named ViewModel extend ViewModel") {
            it.extendsAnyNamed(VIEW_MODEL_BASE_CLASSES)
        }
    files
        .filter { it.declaresViewModel() }
        .assertFalse(testName = "ViewModel files expose state only, without Channel or SharedFlow events") { it.usesEventStreams() }
    classes()
        .filter { it.hasAnnotation { annotation -> annotation.name == "HiltViewModel" } }
        .assertTrue(testName = "HiltViewModel classes use Inject constructor") {
            it.primaryConstructor?.hasAnnotation { annotation -> annotation.name == "Inject" } == true
        }
}

private val VIEW_MODEL_BASE_CLASSES = setOf("ViewModel", "AndroidViewModel")

private fun KoParentProvider.extendsAnyNamed(names: Set<String>): Boolean = extendsAnyNamed(names, mutableSetOf(this))

private fun KoParentProvider.extendsAnyNamed(
    names: Set<String>,
    visited: MutableSet<KoParentProvider>,
): Boolean =
    parents(false).any { parent ->
        parent.name in names ||
            (parent.sourceDeclaration as? KoParentProvider)?.let { source ->
                visited.add(source) && source.extendsAnyNamed(names, visited)
            } == true
    }

private val BANNED_EVENT_IMPORTS =
    setOf(
        "kotlinx.coroutines.channels.*",
        "kotlinx.coroutines.channels.Channel",
        "kotlinx.coroutines.channels.ReceiveChannel",
        "kotlinx.coroutines.channels.SendChannel",
        "kotlinx.coroutines.flow.MutableSharedFlow",
        "kotlinx.coroutines.flow.SharedFlow",
        "kotlinx.coroutines.flow.asSharedFlow",
        "kotlinx.coroutines.flow.consumeAsFlow",
        "kotlinx.coroutines.flow.receiveAsFlow",
        "kotlinx.coroutines.flow.shareIn",
    )

private val BANNED_CHANNEL_TYPES = setOf("Channel", "SendChannel", "ReceiveChannel")

private val EVENT_STREAM_CALL =
    Regex("""\b(Channel|MutableSharedFlow)\s*(<[^(){};=]*?>)?\s*\(|\.(shareIn|receiveAsFlow|consumeAsFlow)\s*\(""")

private val TYPE_NAME = Regex("""[\w.]+""")

private val COMMENT_OR_STRING = Regex(""""{3}.*?"{3}|"(?:\\.|[^"\\\n])*"|/\*.*?\*/|//[^\n]*""", RegexOption.DOT_MATCHES_ALL)

private fun KoFileDeclaration.declaresViewModel(): Boolean = classes().any { koClass -> koClass.extendsAnyNamed(VIEW_MODEL_BASE_CLASSES) }

private fun KoFileDeclaration.usesEventStreams(): Boolean =
    hasImport { import ->
        val path =
            import.text
                .removePrefix("import")
                .substringBefore(" as ")
                .trim()
        path in BANNED_EVENT_IMPORTS || path.endsWith("SharedFlow")
    } ||
        declaredTypes().any { type ->
            TYPE_NAME.findAll(type).any { match ->
                val name = match.value.substringAfterLast('.')
                name in BANNED_CHANNEL_TYPES || name.endsWith("SharedFlow")
            }
        } ||
        EVENT_STREAM_CALL.containsMatchIn(text.replace(COMMENT_OR_STRING, ""))

private fun KoFileDeclaration.declaredTypes(): List<String> {
    val functions = functions()
    val variables = functions.flatMap { it.variables } + classes().flatMap { it.initBlocks }.flatMap { it.variables }
    return properties().mapNotNull { it.type?.text } +
        functions.mapNotNull { it.returnType?.text } +
        variables.mapNotNull { it.type?.text }
}
