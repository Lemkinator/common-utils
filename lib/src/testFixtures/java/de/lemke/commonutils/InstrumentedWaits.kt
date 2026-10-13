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
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Waits for the instrumentation to go idle and checks [condition] on the activity every [poll] until it holds. The
 * idle wait never outlasts [timeout]. Throws [IllegalStateException] once [timeout] has passed.
 */
fun <A : Activity> ActivityScenario<A>.waitUntil(
    timeout: Duration = 5.seconds,
    poll: Duration = 10.milliseconds,
    condition: A.() -> Boolean,
) = pollUntil(
    timeout,
    poll,
    settle = { remaining -> awaitInstrumentationIdle(remaining) },
    condition = { read { it.condition() } },
)

private fun awaitInstrumentationIdle(timeout: Duration) {
    val idle = thread(isDaemon = true) { InstrumentationRegistry.getInstrumentation().waitForIdleSync() }
    idle.join(timeout.inWholeMilliseconds)
}

/** True when no smooth scroll, scroll, layout request or adapter update is pending. */
val RecyclerView.isSettled: Boolean
    get() =
        layoutManager?.isSmoothScrolling != true &&
            scrollState == RecyclerView.SCROLL_STATE_IDLE &&
            !isLayoutRequested &&
            !hasPendingAdapterUpdates()
