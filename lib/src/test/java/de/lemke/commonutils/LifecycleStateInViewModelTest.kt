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

import app.cash.turbine.test
import de.lemke.commonutils.ui.utils.stateInViewModel
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class LifecycleStateInViewModelTest : ShouldSpec(
    {
        should("stateInViewModel initial value returned before upstream emits") {
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            autoClose(AutoCloseable { scope.cancel() })
            val state = flowOf(99).stateInViewModel(scope, 0)
            state.value shouldBe 0
        }

        should("stateInViewModel emits upstream value after subscription") {
            runTest {
                flowOf(42).stateInViewModel(backgroundScope, 0).test {
                    awaitItem() shouldBe 0
                    awaitItem() shouldBe 42
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        should("stateInViewModel keeps the upstream for the stop timeout after the last subscriber leaves and restarts it later") {
            runTest {
                var starts = 0
                var active = false
                val upstream =
                    flow {
                        starts++
                        active = true
                        try {
                            emit(1)
                            awaitCancellation()
                        } finally {
                            active = false
                        }
                    }
                val state = upstream.stateInViewModel(backgroundScope, 0)

                val first = launch { state.collect {} }
                runCurrent()
                starts shouldBe 1
                active shouldBe true

                first.cancel()
                runCurrent()
                advanceTimeBy(4_999)
                runCurrent()
                active shouldBe true

                advanceTimeBy(2)
                runCurrent()
                active shouldBe false

                val second = launch { state.collect {} }
                runCurrent()
                starts shouldBe 2
                active shouldBe true
                second.cancel()
            }
        }
    },
)
