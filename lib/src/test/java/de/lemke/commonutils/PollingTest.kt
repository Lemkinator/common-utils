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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

private const val STALLED = "main thread not idle"

private class PollingRun(
    private val settleCost: Duration = Duration.ZERO,
    private val settleResult: Settle = Settle.Settled,
    private val probes: Iterator<Probe> = generateSequence { Probe.NotMet }.iterator(),
) {
    val clock = TestTimeSource()
    val remainings = mutableListOf<Duration>()
    val conditionRemainings = mutableListOf<Duration>()
    val sleeps = mutableListOf<Duration>()

    fun poll(
        timeout: Duration,
        poll: Duration,
    ) = pollUntil(
        timeout,
        poll,
        STALLED,
        settle = {
            remainings += it
            clock += settleCost
            settleResult
        },
        condition = {
            conditionRemainings += it
            probes.next()
        },
        timeSource = clock,
        sleep = {
            sleeps += it
            clock += it
        },
    )
}

class PollingTest : ShouldSpec(
    {
        context("pollUntil") {
            should("give settle the shrinking time left and sleep no longer than what is left") {
                val run = PollingRun(settleCost = 5.milliseconds)

                val error = shouldThrow<IllegalStateException> { run.poll(timeout = 100.milliseconds, poll = 30.milliseconds) }

                error.message shouldBe "condition not met within 100ms"
                run.remainings shouldBe listOf(100.milliseconds, 65.milliseconds, 30.milliseconds)
                run.sleeps shouldBe listOf(30.milliseconds, 30.milliseconds, 25.milliseconds)
            }

            should("cap the sleep at the time left when settle used up most of it") {
                val run = PollingRun(settleCost = 90.milliseconds)

                shouldThrow<IllegalStateException> { run.poll(timeout = 100.milliseconds, poll = 10.seconds) }

                run.sleeps shouldBe listOf(10.milliseconds)
            }

            should("fail without settling or checking when no time is left") {
                val run = PollingRun()

                val error = shouldThrow<IllegalStateException> { run.poll(timeout = Duration.ZERO, poll = 10.milliseconds) }

                error.message shouldBe "condition not met within 0s"
                run.remainings shouldBe emptyList()
                run.conditionRemainings shouldBe emptyList()
            }

            should("return once the condition is met") {
                val run = PollingRun(probes = listOf(Probe.NotMet, Probe.NotMet, Probe.Met).iterator())

                run.poll(timeout = 1.seconds, poll = 1.milliseconds)

                run.conditionRemainings.size shouldBe 3
                run.sleeps shouldBe listOf(1.milliseconds, 1.milliseconds)
            }

            should("throw at once when settle times out") {
                val run = PollingRun(settleResult = Settle.TimedOut)

                val error = shouldThrow<IllegalStateException> { run.poll(timeout = 1.seconds, poll = 1.milliseconds) }

                error.message shouldBe "main thread not idle within 1s"
                run.conditionRemainings shouldBe emptyList()
            }

            should("throw at once when the condition check times out") {
                val run = PollingRun(probes = listOf(Probe.TimedOut).iterator())

                val error = shouldThrow<IllegalStateException> { run.poll(timeout = 1.seconds, poll = 1.milliseconds) }

                error.message shouldBe "main thread not idle within 1s"
                run.sleeps shouldBe emptyList()
            }

            should("give the condition the time left after settle") {
                val run = PollingRun(settleCost = 40.milliseconds, probes = listOf(Probe.Met).iterator())

                run.poll(timeout = 100.milliseconds, poll = 10.milliseconds)

                run.conditionRemainings shouldBe listOf(60.milliseconds)
            }
        }
    },
)
