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
package de.lemke.commonutils.data

import android.util.Log
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit.MILLISECONDS

private const val TAG = "InAppReviewCooldown"
private const val MIN_DAYS_BETWEEN_REVIEWS = 14L

/**
 * `true` once 14 days have passed since [SettingsRepository.lastInAppReview]. A never-set timestamp is seeded with the
 * current time, so the cooldown starts at first launch instead of surfacing a review request immediately.
 */
fun SettingsRepository.canShowInAppReview(): Boolean {
    if (lastInAppReview == 0L) lastInAppReview = currentTimeMillis()
    val daysSinceLastReview = MILLISECONDS.toDays(currentTimeMillis() - lastInAppReview)
    Log.d(TAG, "Days since last review: $daysSinceLastReview")
    return daysSinceLastReview >= MIN_DAYS_BETWEEN_REVIEWS
}

/** Restarts the cooldown from now. */
fun SettingsRepository.markInAppReviewRequested() {
    lastInAppReview = currentTimeMillis()
}
