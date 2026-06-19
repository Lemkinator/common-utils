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

import de.lemke.commonutils.ui.activity.CommonUtilsLibsActivity
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class OnboardingUtilsTest : ShouldSpec(
    {
        // --- setupOnboarding validation ---

        context("setupOnboarding rejects OOBE in steps") {
            should("throw when CommonUtilsOOBEActivity is included") {
                shouldThrow<IllegalArgumentException> {
                    setupOnboarding(listOf(CommonUtilsOOBEActivity::class.java))
                }
            }
        }

        context("setupOnboarding rejects duplicate steps") {
            should("throw when a step appears more than once") {
                shouldThrow<IllegalArgumentException> {
                    setupOnboarding(listOf(CommonUtilsLibsActivity::class.java, CommonUtilsLibsActivity::class.java))
                }
            }
        }

        context("setupOnboarding accepts valid steps") {
            should("accept empty steps without throwing") {
                setupOnboarding(emptyList())
                Onboarding.steps shouldBe emptyList()
            }
            should("accept unique non-OOBE steps without throwing") {
                setupOnboarding(listOf(CommonUtilsLibsActivity::class.java))
                Onboarding.steps shouldBe listOf(CommonUtilsLibsActivity::class.java)
            }
        }

        // --- nextInChain (the chain-walk logic used by advanceOnboarding) ---

        val oobeClass = CommonUtilsOOBEActivity::class.java.name
        val stepA = "com.example.StepA"
        val stepB = "com.example.StepB"

        context("OOBE is the only step") {
            should("return null - no next after OOBE") {
                nextInChain(listOf(oobeClass), oobeClass).shouldBeNull()
            }
        }

        context("OOBE advances to the first configured step") {
            should("return stepA") {
                nextInChain(listOf(oobeClass, stepA), oobeClass) shouldBe stepA
            }
        }

        context("intermediate step advances to the next") {
            should("return stepB after stepA") {
                nextInChain(listOf(oobeClass, stepA, stepB), stepA) shouldBe stepB
            }
        }

        context("last step has no next") {
            should("return null for stepB") {
                nextInChain(listOf(oobeClass, stepA, stepB), stepB).shouldBeNull()
            }
        }

        context("activity not in chain") {
            should("return null") {
                nextInChain(listOf(oobeClass, stepA), stepB).shouldBeNull()
            }
        }
    },
)
