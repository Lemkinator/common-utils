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

import kotlin.time.Duration
import kotlin.time.TimeSource

/** How a [pollUntil] settle step ended. */
internal enum class Settle {
    /** The thing waited on is idle. */
    Settled,

    /** The time given to settle ran out first. */
    TimedOut,
}

/** How a [pollUntil] condition check ended. */
internal enum class Probe {
    Met,
    NotMet,

    /** The time given to run the check ran out first. */
    TimedOut,
}

/**
 * Runs [settle], then [condition], each with the time left before the deadline, every [poll] until the condition is
 * [Probe.Met]. Throws once [timeout] has passed, or at once when a step times out: the message then names [stalled]. A
 * step must return within the time it is given. Waits with [sleep], which tests replace together with [timeSource].
 */
internal fun pollUntil(
    timeout: Duration,
    poll: Duration,
    stalled: String,
    settle: (remaining: Duration) -> Settle,
    condition: (remaining: Duration) -> Probe,
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    sleep: (Duration) -> Unit = { Thread.sleep(it.inWholeMilliseconds) },
) {
    val deadline = timeSource.markNow() + timeout

    fun remaining() = (-deadline.elapsedNow()).coerceAtLeast(Duration.ZERO)
    while (true) {
        check(remaining() > Duration.ZERO) { "condition not met within $timeout" }
        check(settle(remaining()) == Settle.Settled) { "$stalled within $timeout" }
        when (condition(remaining())) {
            Probe.Met -> return
            Probe.NotMet -> sleep(minOf(poll, remaining()))
            Probe.TimedOut -> error("$stalled within $timeout")
        }
    }
}
