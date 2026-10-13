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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeMonotonicallyDecreasing
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class PollingTest : ShouldSpec(
    {
        context("pollUntil") {
            should("give settle a shrinking, non-negative time that never exceeds the timeout") {
                val timeout = 60.milliseconds
                val remainings = mutableListOf<Duration>()

                shouldThrow<IllegalStateException> {
                    pollUntil(
                        timeout,
                        poll = 10.milliseconds,
                        settle = {
                            remainings += it
                            Settle.Settled
                        },
                        condition = { false },
                    )
                }

                remainings shouldHaveAtLeastSize 2
                remainings.shouldBeMonotonicallyDecreasing()
                remainings.forEach {
                    it shouldBeGreaterThanOrEqualTo Duration.ZERO
                    it shouldBeLessThanOrEqualTo timeout
                }
            }

            should("check the condition without settling when no time is left") {
                var settles = 0
                var checks = 0

                val error =
                    shouldThrow<IllegalStateException> {
                        pollUntil(
                            Duration.ZERO,
                            poll = 10.milliseconds,
                            settle = {
                                settles++
                                Settle.Settled
                            },
                            condition = {
                                checks++
                                false
                            },
                        )
                    }

                settles shouldBe 0
                checks shouldBe 1
                error.message shouldBe "condition not met within 0s"
            }

            should("return once the condition holds") {
                var checks = 0

                pollUntil(1000.milliseconds, poll = 1.milliseconds, settle = { Settle.Settled }, condition = { ++checks == 3 })

                checks shouldBe 3
            }

            should("throw at once when settle times out") {
                var checks = 0

                val error =
                    shouldThrow<IllegalStateException> {
                        pollUntil(
                            1000.milliseconds,
                            poll = 1.milliseconds,
                            settle = { Settle.TimedOut },
                            condition = {
                                checks++
                                true
                            },
                        )
                    }

                error.message shouldBe "main thread not idle within 1s"
                checks shouldBe 0
            }
        }
    },
)
