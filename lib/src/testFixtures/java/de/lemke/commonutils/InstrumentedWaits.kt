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
import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Waits for the instrumentation to go idle and checks [condition] on the activity every [poll] until it holds. Throws
 * [IllegalStateException] "condition not met" once [timeout] has passed. When the main thread stays silent for a whole
 * [poll] after that, it throws "main thread not idle" instead. A condition that throws reaches the caller unchanged. On
 * a device, call it from the instrumentation thread: the main thread fails at once. On the main looper thread
 * (Robolectric) every step runs directly and the main thread is never reported silent.
 */
fun <A : Activity> ActivityScenario<A>.waitUntil(
    timeout: Duration = 5.seconds,
    poll: Duration = 10.milliseconds,
    condition: A.() -> Boolean,
) = pollUntil(
    timeout,
    poll,
    stalled = "main thread not idle",
    settle = { remaining -> awaitInstrumentationIdle(remaining) },
    condition = { remaining -> probeOnMainThread(remaining, condition) },
)

private val onMainThread get() = Looper.myLooper() == Looper.getMainLooper()

private fun awaitInstrumentationIdle(timeout: Duration): Boolean {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    if (onMainThread) {
        instrumentation.waitForIdleSync()
        return true
    }
    val idle = CountDownLatch(1)
    instrumentation.waitForIdle { idle.countDown() }
    return idle.await(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
}

private fun <A : Activity> ActivityScenario<A>.probeOnMainThread(
    timeout: Duration,
    condition: A.() -> Boolean,
): Probe {
    if (onMainThread) return read { it.condition() }.toProbe()
    val handler = Handler(Looper.getMainLooper())
    val outcome = AtomicReference<Result<Boolean>>()
    val done = CountDownLatch(1)
    val cancelled = AtomicBoolean(false)
    val task =
        Runnable {
            if (!cancelled.get()) outcome.set(runCatching { read { it.condition() } })
            done.countDown()
        }
    handler.post(task)
    if (!done.await(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)) {
        cancelled.set(true)
        handler.removeCallbacks(task)
        return Probe.TimedOut
    }
    return outcome.get().getOrThrow().toProbe()
}

private fun Boolean.toProbe() = if (this) Probe.Met else Probe.NotMet

/** True when no smooth scroll, scroll, layout request or adapter update is pending. */
val RecyclerView.isSettled: Boolean
    get() =
        layoutManager?.isSmoothScrolling != true &&
            scrollState == RecyclerView.SCROLL_STATE_IDLE &&
            !isLayoutRequested &&
            !hasPendingAdapterUpdates()
