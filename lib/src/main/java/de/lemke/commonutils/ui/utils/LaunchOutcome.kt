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
package de.lemke.commonutils.ui.utils

import android.content.Context

/**
 * Outcome of a helper that starts another app or screen and shows an error toast if that fails, such as [shareText],
 * [openURL], [sendEmail], [exportBitmap] or [advanceOnboarding].
 *
 * A RESUMED collector that launches from UI state marks the state handled on [Started] and [Failed]: the error toast
 * of a failure shows once. On [Dropped] it keeps the state pending, so the launch runs again the next time the activity
 * resumes. If the launch that closed the latch never pauses the activity, the latch reopens after 1 s. No resume follows
 * then, so the pending launch waits until the activity pauses and resumes.
 */
sealed interface LaunchOutcome {
    /** The activity, share sheet or picker was started. */
    data object Started : LaunchOutcome

    /** The activity's launch latch dropped the launch, so nothing started and nothing was shown. */
    data object Dropped : LaunchOutcome

    /** Nothing could be started; the helper showed its error toast. */
    data object Failed : LaunchOutcome
}

/** Runs [start] as a gated launch of this context's activity; a throwing [start] propagates. See [launchGated]. */
internal fun Context.launchForOutcome(start: () -> Unit): LaunchOutcome =
    if (launchGated(start = start)) LaunchOutcome.Started else LaunchOutcome.Dropped
