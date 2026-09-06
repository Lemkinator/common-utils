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

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import de.lemke.commonutils.NoCoverage
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.data.canShowInAppReview
import de.lemke.commonutils.data.markInAppReviewRequested

private const val TAG = "InAppReviewUtils"

/** Attempts to show the in-app review flow; finishes the activity whether the review is shown or skipped. */
@NoCoverage
fun AppCompatActivity.showInAppReviewOrFinish(settings: SettingsRepository) =
    showInAppReview(
        settings,
        onNotAllowed = { finishAfterTransition() },
        onCompleted = { finishAfterTransition() },
    )

/** Requests the in-app review flow if the cooldown period has elapsed; silently skips otherwise. */
@NoCoverage
fun AppCompatActivity.showInAppReviewIfPossible(settings: SettingsRepository) = showInAppReview(settings)

@NoCoverage
@Suppress("TooGenericExceptionCaught")
private fun AppCompatActivity.showInAppReview(
    settings: SettingsRepository,
    onNotAllowed: () -> Unit = {},
    onCompleted: () -> Unit = {},
) {
    if (!settings.canShowInAppReview()) {
        Log.d(TAG, "In app review requested recently, skipping")
        onNotAllowed()
        return
    }
    Log.d(TAG, "trying to show in app review")
    // Stamp the cooldown before launching the flow so repeated rapid calls or process restarts
    // during the request do not hammer Play Core. The 14-day window is intentionally pessimistic.
    settings.markInAppReviewRequested()
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
