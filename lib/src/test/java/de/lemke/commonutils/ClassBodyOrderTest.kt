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

import com.lemonappdev.konsist.core.exception.KoAssertionFailedException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.string.shouldContain

class ClassBodyOrderTest : ShouldSpec() {
    init {
        should("pass a class and an interface in declaration order") {
            val source =
                """
                package demo

                interface Contract {
                    val name: String
                    fun run()
                }

                class Clean : Contract {
                    private val counter = 0

                    init {
                        println(counter)
                    }

                    override val name = "clean"

                    override fun run() = Unit

                    fun extra() = Unit

                    companion object {
                        const val LIMIT = 1
                    }

                    class Nested
                }
                """.trimIndent()

            withSourceScope(source) { scope -> shouldNotThrowAny { scope.assertClassBodyOrder() } }
        }
        listOf(
            "properties declared before functions in class body" to "class A {\n    fun f() = 1\n\n    val x = 1\n}\n",
            "properties declared before functions in class body" to "interface I {\n    fun f()\n\n    val x: Int\n}\n",
            "init blocks declared before functions in class body" to
                "class A {\n    fun f() = 1\n\n    init {\n        println()\n    }\n}\n",
            "override functions declared before non-override functions in class body" to
                "interface B {\n    fun b()\n}\n\nclass A : B {\n    fun a() = 1\n\n    override fun b() = Unit\n}\n",
            "companion object is the last non-class member in class body" to
                "class A {\n    companion object {\n        const val X = 1\n    }\n\n    fun f() = 1\n}\n",
            "non-private nested class declarations are last in class body" to "class A {\n    class Inner\n\n    fun f() = 1\n}\n",
            "non-private nested class declarations are last in class body" to
                "interface I {\n    interface Inner\n\n    val x: Int\n}\n",
        ).forEach { (rule, body) ->
            should("fail with the rule name: $rule (${body.lineSequence().first()})") {
                withSourceScope("package demo\n\n$body") { scope ->
                    shouldThrow<KoAssertionFailedException> { scope.assertClassBodyOrder() }.message shouldContain rule
                }
            }
        }
        should("accept a private nested class before a function") {
            withSourceScope("package demo\n\nclass A {\n    private class Inner\n\n    fun f() = 1\n}\n") { scope ->
                shouldNotThrowAny { scope.assertClassBodyOrder() }
            }
        }
    }
}
