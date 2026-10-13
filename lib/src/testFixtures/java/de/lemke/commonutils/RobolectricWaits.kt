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

import android.os.Looper
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.robolectric.Shadows.shadowOf

/** Runs every task that is due on the Robolectric main looper. */
fun idleMainLooper() {
    shadowOf(Looper.getMainLooper()).idle()
}

/**
 * Idles the main looper and checks [condition] every [poll] until it holds, for work on a background thread that posts
 * back to the main looper. Throws [IllegalStateException] once [timeout] has passed.
 */
fun awaitMainLooperUntil(
    timeout: Duration = 5.seconds,
    poll: Duration = 10.milliseconds,
    condition: () -> Boolean,
) = pollUntil(
    timeout,
    poll,
    stalled = "main looper not idle",
    settle = { true },
    condition = {
        idleMainLooper()
        if (condition()) Probe.Met else Probe.NotMet
    },
)
