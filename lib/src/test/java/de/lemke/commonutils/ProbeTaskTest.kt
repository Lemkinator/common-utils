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
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ProbeTaskTest : ShouldSpec(
    {
        context("ProbeTask") {
            should("report the check result once it has run") {
                ProbeTask { true }.apply { run() }.await(1.seconds) shouldBe Probe.Met
                ProbeTask { false }.apply { run() }.await(1.seconds) shouldBe Probe.NotMet
            }

            should("time out when the task has not run") {
                ProbeTask { true }.await(1.milliseconds) shouldBe Probe.TimedOut
            }

            should("skip the check when it runs after its caller timed out (18)") {
                var calls = 0
                val task =
                    ProbeTask {
                        calls++
                        true
                    }
                task.await(1.milliseconds) shouldBe Probe.TimedOut

                task.run()

                calls shouldBe 0
            }

            should("rethrow the exception of the check with its type and message (14)") {
                val task = ProbeTask { throw UnsupportedOperationException("boom") }
                task.run()

                shouldThrow<UnsupportedOperationException> { task.await(1.seconds) }.message shouldBe "boom"
            }
        }
    },
)
