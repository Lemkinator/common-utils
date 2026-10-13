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

import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** How a [pollUntil] condition check ended. */
internal enum class Probe {
    Met,
    NotMet,

    /** The check got no answer within the time it was given. */
    TimedOut,
}

/** How long the main thread gets to answer the final probe before it counts as stalled. */
internal val STALL_GRACE = 1.seconds

/**
 * Settles, then probes [condition], every [poll] until it is [Probe.Met]. [settle] returns false when it did not finish
 * in the time it is given. Once no time is left, or a step runs out of time, one final probe decides. It gets the
 * larger of [poll] and [stallGrace] to answer: [Probe.Met] returns, [Probe.NotMet] throws "condition not met", and
 * [Probe.TimedOut] throws a message naming [stalled]. Every call probes at least once. A step must return within the
 * time it is given. [timeSource] and [sleep] are replaced by tests.
 */
internal fun pollUntil(
    timeout: Duration,
    poll: Duration,
    stalled: String,
    settle: (budget: Duration) -> Boolean,
    condition: (budget: Duration) -> Probe,
    stallGrace: Duration = STALL_GRACE,
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    sleep: (Duration) -> Unit = { TimeUnit.NANOSECONDS.sleep(it.inWholeNanoseconds) },
) {
    val deadline = timeSource.markNow() + timeout

    fun remaining() = (-deadline.elapsedNow()).coerceAtLeast(Duration.ZERO)
    while (true) {
        val toSettle = remaining()
        if (toSettle == Duration.ZERO || !settle(toSettle)) break
        val toProbe = remaining()
        if (toProbe == Duration.ZERO) break
        when (condition(toProbe)) {
            Probe.Met -> return
            Probe.NotMet -> Unit
            Probe.TimedOut -> break
        }
        val toSleep = minOf(poll, remaining())
        if (toSleep > Duration.ZERO) sleep(toSleep)
    }
    when (condition(maxOf(poll, stallGrace))) {
        Probe.Met -> Unit
        Probe.NotMet -> error("condition not met within $timeout")
        Probe.TimedOut -> error("$stalled within $timeout")
    }
}
