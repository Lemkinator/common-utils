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
package de.lemke.commonutils.data

import android.content.SharedPreferences
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class FakeSharedPreferencesTest : ShouldSpec(
    {
        val finishes =
            mapOf<String, (SharedPreferences.Editor) -> Unit>(
                "commit" to { it.commit() },
                "apply" to { it.apply() },
            )

        lateinit var prefs: FakeSharedPreferences
        lateinit var notifiedKeys: MutableList<String?>

        // Android's order of per-key notifications is undocumented; only the clear's null key comes first.
        fun notifiedClearThen(vararg keys: String) {
            notifiedKeys.first() shouldBe null
            notifiedKeys.drop(1) shouldContainExactlyInAnyOrder keys.toList()
        }

        beforeEach {
            prefs = FakeSharedPreferences()
            prefs
                .edit()
                .putString("old", "o")
                .putString("removed", "r")
                .commit()
            notifiedKeys = mutableListOf()
            prefs.registerOnSharedPreferenceChangeListener { _, key -> notifiedKeys += key }
        }

        finishes.forEach { (name, finish) ->
            context("clear() finished by $name") {
                should("keep a put issued before clear()") {
                    finish(prefs.edit().putString("a", "x").clear())

                    prefs.all shouldBe mapOf("a" to "x")
                    notifiedClearThen("a")
                }

                should("keep a put issued after clear()") {
                    finish(prefs.edit().clear().putString("a", "x"))

                    prefs.all shouldBe mapOf("a" to "x")
                    notifiedClearThen("a")
                }

                should("re-store a put of a cleared key with its old value") {
                    finish(prefs.edit().putString("old", "o").clear())

                    prefs.all shouldBe mapOf("old" to "o")
                    notifiedClearThen("old")
                }

                should("apply a remove issued before clear() as a no-op") {
                    finish(
                        prefs
                            .edit()
                            .remove("removed")
                            .clear()
                            .putInt("n", 1),
                    )

                    prefs.all shouldBe mapOf("n" to 1)
                    notifiedClearThen("n")
                }

                should("apply a remove issued after clear() as a no-op") {
                    finish(prefs.edit().clear().remove("old"))

                    prefs.all shouldBe emptyMap()
                    notifiedKeys shouldBe listOf(null)
                }

                should("notify once with a null key, then once per put") {
                    finish(
                        prefs
                            .edit()
                            .putString("a", "x")
                            .clear()
                            .putBoolean("b", true),
                    )

                    prefs.all shouldBe mapOf("a" to "x", "b" to true)
                    notifiedClearThen("a", "b")
                }

                should("not replay the clear on a reused editor") {
                    val editor = prefs.edit().clear()
                    finish(editor)
                    finish(editor.putString("a", "x"))

                    prefs.all shouldBe mapOf("a" to "x")
                    notifiedClearThen("a")
                }
            }
        }

        should("return true from commit() of a clear") {
            prefs
                .edit()
                .putString("a", "x")
                .clear()
                .commit()
                .shouldBeTrue()

            prefs.all shouldBe mapOf("a" to "x")
        }
    },
)
