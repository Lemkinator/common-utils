@file:Suppress("unused")

package de.lemke.commonutils

import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit.MILLISECONDS

private const val TAG = "InAppReviewUtils"

fun AppCompatActivity.getLastInAppReview() = getSharedPreferences(TAG, MODE_PRIVATE).getLong("lastInAppReview", currentTimeMillis())

fun AppCompatActivity.setInAppReview() = getSharedPreferences(TAG, MODE_PRIVATE).edit { putLong("lastInAppReview", currentTimeMillis()) }

fun AppCompatActivity.canShowInAppReview() = try {
    val daysSinceLastReview = MILLISECONDS.toDays(currentTimeMillis() - getLastInAppReview())
    Log.d(TAG, "Days since last review: $daysSinceLastReview")
    daysSinceLastReview >= 14
} catch (e: Exception) {
    Log.e(TAG, "Error checking in-app review eligibility", e)
    false
}

fun AppCompatActivity.showInAppReviewOrFinish() = showInAppReviewAnd { finishAfterTransition() }

fun AppCompatActivity.showInAppReviewIfPossible() = showInAppReview()

private fun AppCompatActivity.showInAppReviewAnd(action: () -> Unit) {
    showInAppReview(
        onNotAllowed = { action() },
        onCompleted = { action() },
        onReviewError = { task -> action() },
        onError = { e -> action() }
    )
}

private fun AppCompatActivity.showInAppReview(
    onNotAllowed: () -> Unit = {},
    onCompleted: () -> Unit = {},
    onReviewError: (Task<ReviewInfo>) -> Unit = {},
    onError: (Exception) -> Unit = {},
) {
    try {
        if (!canShowInAppReview()) {
            Log.d(TAG, "In app review requested less than 14 days ago, skipping")
            onNotAllowed()
            return
        }
        Log.d(TAG, "trying to show in app review")
        setInAppReview()
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Review task successful")
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
                    Log.d(TAG, "Review flow complete")
                    onCompleted()
                }
            } else {
                Log.e(TAG, "Review task failed: ${task.exception?.message}")
                onReviewError(task)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error showing in-app review", e)
        onError(e)
    }
}
