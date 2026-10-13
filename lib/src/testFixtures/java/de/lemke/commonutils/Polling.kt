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

/**
 * Runs [settle] with the time left before the deadline while any is left, then checks [condition], every [poll] until it
 * holds. Throws once [timeout] has passed, or at once when [settle] times out. [settle] must return within the time it
 * is given.
 */
internal fun pollUntil(
    timeout: Duration,
    poll: Duration,
    settle: (remaining: Duration) -> Settle,
    condition: () -> Boolean,
) {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    while (true) {
        val remaining = (-deadline.elapsedNow()).coerceAtLeast(Duration.ZERO)
        check(remaining == Duration.ZERO || settle(remaining) == Settle.Settled) { "main thread not idle within $timeout" }
        if (condition()) return
        check(!deadline.hasPassedNow()) { "condition not met within $timeout" }
        Thread.sleep(minOf(poll, remaining).inWholeMilliseconds)
    }
}
