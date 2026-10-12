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
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInitBlockDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue

/**
 * Fails if a class or interface body in this scope breaks the declaration order. Each rule is its own assertion, so
 * a failure names the broken rule: properties before functions, init blocks before functions, override functions
 * before other functions, the companion object after all properties, functions and init blocks, and non-private
 * nested types last.
 */
fun KoScope.assertClassBodyOrder() {
    classBodyRules.forEach { (testName, holds) ->
        classes().assertTrue(testName = testName) { holds(it.body()) }
        interfaces().assertTrue(testName = testName) { holds(it.body()) }
    }
}

private class ClassBody(
    val declarations: List<Any>,
    val functions: List<KoFunctionDeclaration>,
    val companion: KoObjectDeclaration?,
)

private fun KoClassDeclaration.body() =
    ClassBody(
        declarations = declarations(includeNested = false, includeLocal = false),
        functions = functions(includeNested = false, includeLocal = false),
        companion = objects(includeNested = false).lastOrNull { it.hasModifier(KoModifier.COMPANION) },
    )

private fun KoInterfaceDeclaration.body() =
    ClassBody(
        declarations = declarations(includeNested = false, includeLocal = false),
        functions = functions(includeNested = false, includeLocal = false),
        companion = objects(includeNested = false).lastOrNull { it.hasModifier(KoModifier.COMPANION) },
    )

private val classBodyRules: List<Pair<String, (ClassBody) -> Boolean>> =
    listOf(
        "properties declared before functions in class body" to { body ->
            body.declarations.allBefore({ it is KoPropertyDeclaration }, { it is KoFunctionDeclaration })
        },
        "init blocks declared before functions in class body" to { body ->
            body.declarations.allBefore({ it is KoInitBlockDeclaration }, { it is KoFunctionDeclaration })
        },
        "override functions declared before non-override functions in class body" to { body ->
            body.functions.allBefore({ it.hasModifier(KoModifier.OVERRIDE) }, { !it.hasModifier(KoModifier.OVERRIDE) })
        },
        "companion object is the last non-class member in class body" to { body ->
            val companion = body.companion
            companion == null ||
                body.declarations.drop(body.declarations.indexOf(companion) + 1).none { it.isPropertyFunctionOrInit() }
        },
        "non-private nested class declarations are last in class body" to { body ->
            val firstNestedType = body.declarations.indexOfFirst { it.isNonPrivateNestedType() }
            val lastMember =
                body.declarations.indexOfLast {
                    it.isPropertyFunctionOrInit() || (it is KoObjectDeclaration && it.hasModifier(KoModifier.COMPANION))
                }
            firstNestedType == -1 || lastMember == -1 || firstNestedType > lastMember
        },
    )

private fun <T> List<T>.allBefore(
    first: (T) -> Boolean,
    second: (T) -> Boolean,
): Boolean {
    val lastFirst = indexOfLast(first)
    val firstSecond = indexOfFirst(second)
    return lastFirst == -1 || firstSecond == -1 || lastFirst < firstSecond
}

private fun Any.isPropertyFunctionOrInit() =
    this is KoPropertyDeclaration || this is KoFunctionDeclaration || this is KoInitBlockDeclaration

private fun Any.isNonPrivateNestedType() =
    when (this) {
        is KoClassDeclaration -> !hasModifier(KoModifier.PRIVATE)
        is KoInterfaceDeclaration -> !hasModifier(KoModifier.PRIVATE)
        is KoObjectDeclaration -> !hasModifier(KoModifier.COMPANION) && !hasModifier(KoModifier.PRIVATE)
        else -> false
    }
