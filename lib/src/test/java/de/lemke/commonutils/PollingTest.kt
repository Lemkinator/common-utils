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
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

private const val STALLED = "main thread not idle"

/** A probe result and what the probe costs; a probe that costs more than its budget uses the budget up and times out. */
private class Answer(
    val probe: Probe,
    val cost: Duration = Duration.ZERO,
)

private class PollingRun(
    private val settleCost: Duration = Duration.ZERO,
    probes: List<Answer> = emptyList(),
    private val failure: Throwable? = null,
) {
    private val clock = TestTimeSource()
    private val queued = probes.iterator()
    val settleBudgets = mutableListOf<Duration>()
    val probeBudgets = mutableListOf<Duration>()
    val sleeps = mutableListOf<Duration>()

    fun poll(
        timeout: Duration,
        poll: Duration = 10.milliseconds,
    ) = pollUntil(
        timeout,
        poll,
        STALLED,
        settle = { budget ->
            settleBudgets += budget
            clock += minOf(settleCost, budget)
            settleCost <= budget
        },
        condition = { budget ->
            probeBudgets += budget
            failure?.let { throw it }
            val answer = if (queued.hasNext()) queued.next() else Answer(Probe.NotMet)
            clock += minOf(answer.cost, budget)
            if (answer.cost > budget) Probe.TimedOut else answer.probe
        },
        timeSource = clock,
        sleep = {
            sleeps += it
            clock += it
        },
    )

    fun shouldFailWith(
        message: String,
        timeout: Duration,
        poll: Duration = 10.milliseconds,
    ) {
        shouldThrow<IllegalStateException> { poll(timeout, poll) }.message shouldBe message
    }
}

class PollingTest : ShouldSpec(
    {
        context("pollUntil") {
            should("return when the condition holds on the first probe with time left (1)") {
                val run = PollingRun(probes = listOf(Answer(Probe.Met)))

                run.poll(timeout = 100.milliseconds)

                run.settleBudgets shouldBe listOf(100.milliseconds)
                run.probeBudgets shouldBe listOf(100.milliseconds)
                run.sleeps shouldBe emptyList()
            }

            should("sleep a poll and return once the condition turns true (2)") {
                val run = PollingRun(probes = listOf(Answer(Probe.NotMet), Answer(Probe.Met)))

                run.poll(timeout = 100.milliseconds)

                run.sleeps shouldBe listOf(10.milliseconds)
                run.settleBudgets shouldBe listOf(100.milliseconds, 90.milliseconds)
            }

            should("sleep exactly the time left when less than a poll remains, then decide on the final probe (3)") {
                val run = PollingRun(settleCost = 25.milliseconds)

                run.shouldFailWith("condition not met within 30ms", timeout = 30.milliseconds)

                run.sleeps shouldBe listOf(5.milliseconds)
                run.settleBudgets shouldBe listOf(30.milliseconds)
                run.probeBudgets shouldBe listOf(5.milliseconds, STALL_GRACE)
            }

            should("sleep sub-millisecond remainders without truncating them (4)") {
                val run = PollingRun(settleCost = 100.milliseconds - 500.microseconds)

                run.shouldFailWith("condition not met within 100ms", timeout = 100.milliseconds)

                run.sleeps shouldBe listOf(500.microseconds)
            }

            should("return when the condition turns true during the last sleep (5)") {
                val run = PollingRun(probes = listOf(Answer(Probe.NotMet), Answer(Probe.Met)))

                run.poll(timeout = 10.milliseconds, poll = 10.milliseconds)

                run.sleeps shouldBe listOf(10.milliseconds)
                run.settleBudgets shouldBe listOf(10.milliseconds)
                run.probeBudgets shouldBe listOf(10.milliseconds, STALL_GRACE)
            }

            should("fail with the condition message when the final probe is not met (6)") {
                val run = PollingRun()

                run.shouldFailWith("condition not met within 50ms", timeout = 50.milliseconds, poll = 20.milliseconds)

                run.sleeps shouldBe listOf(20.milliseconds, 20.milliseconds, 10.milliseconds)
                run.probeBudgets.last() shouldBe STALL_GRACE
            }

            should("check once without settling at timeout zero and return when it holds (7)") {
                val run = PollingRun(probes = listOf(Answer(Probe.Met)))

                run.poll(timeout = Duration.ZERO)

                run.settleBudgets shouldBe emptyList()
                run.probeBudgets shouldBe listOf(STALL_GRACE)
            }

            should("fail at timeout zero when the one check is not met (8)") {
                val run = PollingRun()

                run.shouldFailWith("condition not met within 0s", timeout = Duration.ZERO)

                run.settleBudgets shouldBe emptyList()
                run.sleeps shouldBe emptyList()
            }

            should("give settle and the probe the infinite budget and sleep a poll when the timeout is infinite (9)") {
                val run = PollingRun(probes = listOf(Answer(Probe.NotMet), Answer(Probe.NotMet), Answer(Probe.Met)))

                run.poll(timeout = Duration.INFINITE)

                run.settleBudgets shouldBe List(3) { Duration.INFINITE }
                run.probeBudgets shouldBe List(3) { Duration.INFINITE }
                run.sleeps shouldBe listOf(10.milliseconds, 10.milliseconds)
            }

            should("report the condition message when settle runs out and the final probe is not met (10)") {
                val run = PollingRun(settleCost = 1.seconds)

                run.shouldFailWith("condition not met within 100ms", timeout = 100.milliseconds)

                run.probeBudgets shouldBe listOf(STALL_GRACE)
            }

            should("return when settle runs out and the final probe is met (11)") {
                val run = PollingRun(settleCost = 1.seconds, probes = listOf(Answer(Probe.Met)))

                run.poll(timeout = 100.milliseconds)

                run.probeBudgets shouldBe listOf(STALL_GRACE)
            }

            should("name the stalled main thread when settle runs out and the final probe gets no answer (12)") {
                val run = PollingRun(settleCost = 1.seconds, probes = listOf(Answer(Probe.NotMet, cost = 2.seconds)))

                run.shouldFailWith("$STALLED within 100ms", timeout = 100.milliseconds)
            }

            should("fail with the condition message when a probe runs out in the loop and the final probe is not met (13a)") {
                val run = PollingRun(probes = listOf(Answer(Probe.NotMet, cost = 2.seconds), Answer(Probe.NotMet)))

                run.shouldFailWith("condition not met within 100ms", timeout = 100.milliseconds)

                run.probeBudgets shouldBe listOf(100.milliseconds, STALL_GRACE)
            }

            should("return when a probe runs out in the loop and the final probe is met (13b)") {
                val run = PollingRun(probes = listOf(Answer(Probe.NotMet, cost = 2.seconds), Answer(Probe.Met)))

                run.poll(timeout = 100.milliseconds)

                run.probeBudgets shouldBe listOf(100.milliseconds, STALL_GRACE)
            }

            should("name the stalled main thread when a probe runs out in the loop and the final probe gets no answer (13c)") {
                val run =
                    PollingRun(probes = listOf(Answer(Probe.NotMet, cost = 2.seconds), Answer(Probe.NotMet, cost = 2.seconds)))

                run.shouldFailWith("$STALLED within 100ms", timeout = 100.milliseconds)
            }

            should("skip the in-loop probe when settle used the whole budget and let the final probe decide (21)") {
                val run = PollingRun(settleCost = 100.milliseconds, probes = listOf(Answer(Probe.Met)))

                run.poll(timeout = 100.milliseconds)

                run.settleBudgets shouldBe listOf(100.milliseconds)
                run.probeBudgets shouldBe listOf(STALL_GRACE)
                run.sleeps shouldBe emptyList()
            }

            should("fail with the condition message when settle used the whole budget and the final probe is not met (21)") {
                val run = PollingRun(settleCost = 100.milliseconds)

                run.shouldFailWith("condition not met within 100ms", timeout = 100.milliseconds)

                run.probeBudgets shouldBe listOf(STALL_GRACE)
            }

            should("give the final probe the poll when it is longer than the grace (22)") {
                val run = PollingRun()

                run.shouldFailWith("condition not met within 100ms", timeout = 100.milliseconds, poll = 5.seconds)

                run.probeBudgets.last() shouldBe 5.seconds
            }

            should("rethrow the exception of the condition unchanged (14)") {
                val failure = UnsupportedOperationException("boom")
                val run = PollingRun(failure = failure)

                shouldThrow<UnsupportedOperationException> { run.poll(timeout = 100.milliseconds) } shouldBeSameInstanceAs failure

                run.probeBudgets.size shouldBe 1
                run.sleeps shouldBe emptyList()
            }
        }
    },
)
