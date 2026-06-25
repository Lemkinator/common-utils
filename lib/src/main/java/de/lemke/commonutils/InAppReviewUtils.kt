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

import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit.MILLISECONDS

private const val TAG = "InAppReviewUtils"
private const val MIN_DAYS_BETWEEN_REVIEWS = 14L

/**
 * Returns the timestamp of the last in-app review request in milliseconds.
 * On the first call (no stored timestamp), records the current time so the 14-day cooldown
 * starts from first launch — avoids surfacing a review request immediately on first launch.
 */
fun AppCompatActivity.getLastInAppReview(): Long {
    val prefs = getSharedPreferences(TAG, MODE_PRIVATE)
    if (!prefs.contains("lastInAppReview")) prefs.edit { putLong("lastInAppReview", currentTimeMillis()) }
    return prefs.getLong("lastInAppReview", currentTimeMillis())
}

/** Persists the current time as the last in-app review timestamp. */
fun AppCompatActivity.setInAppReview() = getSharedPreferences(TAG, MODE_PRIVATE).edit { putLong("lastInAppReview", currentTimeMillis()) }

/** Returns `true` if at least 14 days have passed since the last in-app review was shown. */
fun AppCompatActivity.canShowInAppReview(): Boolean {
    val daysSinceLastReview = MILLISECONDS.toDays(currentTimeMillis() - getLastInAppReview())
    Log.d(TAG, "Days since last review: $daysSinceLastReview")
    return daysSinceLastReview >= MIN_DAYS_BETWEEN_REVIEWS
}

/** Attempts to show the in-app review flow; finishes the activity whether the review is shown or skipped. */
@NoCoverage
fun AppCompatActivity.showInAppReviewOrFinish() =
    showInAppReview(
        onNotAllowed = { finishAfterTransition() },
        onCompleted = { finishAfterTransition() },
    )

/** Requests the in-app review flow if the cooldown period has elapsed; silently skips otherwise. */
@NoCoverage
fun AppCompatActivity.showInAppReviewIfPossible() = showInAppReview()

@NoCoverage
@Suppress("TooGenericExceptionCaught")
private fun AppCompatActivity.showInAppReview(
    onNotAllowed: () -> Unit = {},
    onCompleted: () -> Unit = {},
) {
    if (!canShowInAppReview()) {
        Log.d(TAG, "In app review requested less than $MIN_DAYS_BETWEEN_REVIEWS days ago, skipping")
        onNotAllowed()
        return
    }
    Log.d(TAG, "trying to show in app review")
    // Stamp the cooldown before launching the flow so repeated rapid calls or process restarts
    // during the request do not hammer Play Core. The 14-day window is intentionally pessimistic.
    setInAppReview()
    try {
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            onReviewFlowRequested(manager, task, onCompleted)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error showing in-app review", e)
        onCompleted()
    }
}

@NoCoverage
private fun AppCompatActivity.onReviewFlowRequested(
    manager: ReviewManager,
    task: Task<ReviewInfo>,
    onCompleted: () -> Unit,
) {
    if (task.isSuccessful) {
        Log.d(TAG, "Review task successful")
        manager.launchReviewFlow(this, task.result).addOnCompleteListener {
            Log.d(TAG, "Review flow complete")
            onCompleted()
        }
    } else {
        Log.e(TAG, "Review task failed: ${task.exception?.message}")
        onCompleted()
    }
}
