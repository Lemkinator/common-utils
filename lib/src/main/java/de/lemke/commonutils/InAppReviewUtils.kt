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
@file:Suppress("unused")

package de.lemke.commonutils

import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit.MILLISECONDS

private const val TAG = "InAppReviewUtils"
private const val MIN_DAYS_BETWEEN_REVIEWS = 14L

fun AppCompatActivity.getLastInAppReview() = getSharedPreferences(TAG, MODE_PRIVATE).getLong("lastInAppReview", currentTimeMillis())

fun AppCompatActivity.setInAppReview() = getSharedPreferences(TAG, MODE_PRIVATE).edit { putLong("lastInAppReview", currentTimeMillis()) }

@Suppress("TooGenericExceptionCaught")
fun AppCompatActivity.canShowInAppReview() =
    try {
        val daysSinceLastReview = MILLISECONDS.toDays(currentTimeMillis() - getLastInAppReview())
        Log.d(TAG, "Days since last review: $daysSinceLastReview")
        daysSinceLastReview >= MIN_DAYS_BETWEEN_REVIEWS
    } catch (e: Exception) {
        Log.e(TAG, "Error checking in-app review eligibility", e)
        false
    }

fun AppCompatActivity.showInAppReviewOrFinish() = showInAppReview(
    onNotAllowed = { finishAfterTransition() },
    onCompleted = { finishAfterTransition() },
)

fun AppCompatActivity.showInAppReviewIfPossible() = showInAppReview()

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
    setInAppReview()
    try {
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { task ->
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
    } catch (e: Exception) {
        Log.e(TAG, "Error showing in-app review", e)
        onCompleted()
    }
}
