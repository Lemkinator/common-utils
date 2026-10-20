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
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * Fails on an app-declared extension whose receiver lives in `de.lemke.commonutils`.
 *
 * It covers extension functions and properties of any visibility, top-level, member and local. Files whose package
 * starts with `de.lemke.commonutils` are skipped: the library defines its own extensions. The failure message lists
 * `projectPath:line: Receiver.name`, one per line, with `/` as the path separator on every platform.
 *
 * Konsist has no type resolution, so the receiver is matched by name: the first segment of its type name must be an
 * explicit or aliased import from `de.lemke.commonutils`, or the receiver must be written fully qualified. A name
 * that only a wildcard import from `de.lemke.commonutils` can supply counts too, unless the scope declares a type
 * of that name in the file's package, the file has a wildcard import from another package (the name stays ambiguous),
 * or it is a Kotlin or `java.lang` default type such as `String`. Not detected: a type alias of a library type, a
 * type parameter with a library bound (`fun <T : LaunchOutcome> T.x()`), a function-type receiver, and a library
 * type reached through a wildcard import of a package that holds a same-named type outside the scope.
 */
fun KoScope.assertNoCommonUtilsTypeExtensions() {
    val typesByPackage =
        files
            .groupBy({ it.packagee?.name.orEmpty() }, { it.declaredTypeNames() })
            .mapValues { (_, names) -> names.flatten().toSet() }
    val hits =
        files
            .filterNot { it.isInCommonUtils() }
            .flatMap { file ->
                file.extensions().filter { file.receiverIsCommonUtils(it.receiver, typesByPackage) }.map { extension ->
                    Triple(portableProjectPath(file.projectPath), extension.line, "${extension.receiver}.${extension.name}")
                }
            }.sortedWith(compareBy<Triple<String, Int, String>>({ it.first }, { it.second }))
    if (hits.isNotEmpty()) {
        val header =
            if (hits.size == 1) {
                "1 extension on a common-utils type is declared in the app:"
            } else {
                "${hits.size} extensions on common-utils types are declared in the app:"
            }
        throw AssertionError(hits.joinToString("\n", prefix = "$header\n") { (path, line, text) -> "$path:$line: $text" })
    }
}

private const val COMMON_UTILS_PACKAGE = "de.lemke.commonutils"

private val receiverTypeName = Regex("""^(?:@[\w.:]+(?:\([^)]*\))?\s+)*([A-Za-z_][\w.]*)""")
private val declarationKeyword = Regex("""\b(fun|val|var)\b""")

private val defaultTypeNames =
    """
    Any Nothing Unit Boolean Byte Short Int Long Float Double Char Number String CharSequence Comparable Enum Annotation
    Throwable Exception Error RuntimeException IllegalArgumentException IllegalStateException UnsupportedOperationException
    NumberFormatException Array BooleanArray ByteArray ShortArray IntArray LongArray FloatArray DoubleArray CharArray
    Iterable MutableIterable Iterator MutableIterator Collection MutableCollection List MutableList Set MutableSet Map
    MutableMap Sequence Pair Triple Result Lazy Regex IntRange LongRange CharRange Function StringBuilder Appendable
    Object Class Thread Runnable Process Void
    """.trim().split(Regex("""\s+""")).toSet()

private class Extension(
    val receiver: String,
    val name: String,
    val line: Int,
)

private fun KoFileDeclaration.isInCommonUtils(): Boolean =
    packagee?.name.orEmpty().let { it == COMMON_UTILS_PACKAGE || it.startsWith("$COMMON_UTILS_PACKAGE.") }

private fun KoFileDeclaration.declaredTypeNames(): List<String> =
    classes(includeNested = true).map { it.name } + interfaces(includeNested = true).map { it.name } +
        objects(includeNested = true).map { it.name }

private fun KoFileDeclaration.extensions(): List<Extension> {
    val taken = mutableSetOf<Int>()
    val functions =
        functions(includeNested = true, includeLocal = true).mapNotNull { function ->
            function.receiverType?.text?.let { Triple(it, function.name, function.text) }
        }
    val properties =
        properties(includeNested = true).mapNotNull { property ->
            property.receiverType?.text?.let { Triple(it, property.name, property.text) }
        }
    return (functions + properties).mapNotNull { (receiverText, name, declarationText) ->
        receiverTypeName.find(receiverText)?.let { Extension(it.groupValues[1], name, lineOf(declarationText, taken)) }
    }
}

/** The line of the declaration keyword of [declaration] in this file, skipping its KDoc; [taken] keeps equal texts apart. */
private fun KoFileDeclaration.lineOf(
    declaration: String,
    taken: MutableSet<Int>,
): Int {
    var start = text.indexOf(declaration)
    while (start in taken) start = text.indexOf(declaration, start + 1)
    if (start < 0) return 1
    taken += start
    val kdocLength = if (declaration.startsWith("/*")) declaration.indexOf("*/") + 2 else 0
    val keyword = declarationKeyword.find(declaration, kdocLength)?.range?.first ?: kdocLength
    return 1 + text.substring(0, start + keyword).count { it == '\n' }
}

private fun KoFileDeclaration.receiverIsCommonUtils(
    receiver: String,
    typesByPackage: Map<String, Set<String>>,
): Boolean {
    val head = receiver.substringBefore('.')
    val named = imports.filter { !it.isWildcard && (it.alias?.name ?: it.name.substringAfterLast('.')) == head }
    val wildcardPackages = imports.filter { it.isWildcard }.map { it.name.removeSuffix(".*").removeSuffix(".") }
    return when {
        receiver.startsWith("$COMMON_UTILS_PACKAGE.") -> true
        named.isNotEmpty() -> named.any { it.name.isCommonUtilsName() }
        head in typesByPackage[packagee?.name.orEmpty()].orEmpty() || head in defaultTypeNames -> false
        else -> wildcardPackages.any { it.isCommonUtilsName() } && wildcardPackages.all { it.isCommonUtilsName() }
    }
}

private fun String.isCommonUtilsName(): Boolean = this == COMMON_UTILS_PACKAGE || startsWith("$COMMON_UTILS_PACKAGE.")
