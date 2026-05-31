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

import android.app.Activity
import de.lemke.commonutils.ui.activity.CommonUtilsLibsActivity
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class FirstRunFlowTest : ShouldSpec(
    {
        afterEach { FirstRunFlow.steps = emptyList() }

        context("OOBE is the last step when no app steps are configured") {
            should("return null for the next step") {
                FirstRunFlow.steps = emptyList()
                nextFirstRunStep(CommonUtilsOOBEActivity::class.java) shouldBe null
            }
        }

        context("OOBE advances to the first configured app step") {
            should("return the first app step") {
                FirstRunFlow.steps = listOf(CommonUtilsLibsActivity::class.java)
                nextFirstRunStep(CommonUtilsOOBEActivity::class.java) shouldBe CommonUtilsLibsActivity::class.java
            }
        }

        context("the last configured app step has no next") {
            should("return null") {
                FirstRunFlow.steps = listOf(CommonUtilsLibsActivity::class.java)
                nextFirstRunStep(CommonUtilsLibsActivity::class.java) shouldBe null
            }
        }

        should("unregistered activity returns null") {
            FirstRunFlow.steps = emptyList()
            nextFirstRunStep(Activity::class.java) shouldBe null
        }
    },
)
