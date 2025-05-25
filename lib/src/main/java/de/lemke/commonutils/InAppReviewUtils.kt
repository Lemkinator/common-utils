@file:Suppress("unused")

package de.lemke.commonutils

import android.content.Context.MODE_PRIVATE
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit.MILLISECONDS

private const val tag = "InAppReviewUtils"

fun AppCompatActivity.getLastInAppReview() = getSharedPreferences(tag, MODE_PRIVATE).getLong("lastInAppReview", currentTimeMillis())
fun AppCompatActivity.setInAppReview() = getSharedPreferences(tag, MODE_PRIVATE).edit { putLong("lastInAppReview", currentTimeMillis()) }
fun AppCompatActivity.canShowInAppReview() = try {
    val daysSinceLastReview = MILLISECONDS.toDays(currentTimeMillis() - getLastInAppReview())
    Log.d(tag, "Days since last review: $daysSinceLastReview")
    daysSinceLastReview >= 14
} catch (e: Exception) {
    e.printStackTrace()
    false
}

fun AppCompatActivity.showInAppReviewOrFinish() {
    try {
        if (canShowInAppReview()) {
            Log.d(tag, "In app review requested less than 14 days ago, skipping")
            finishAfterTransition()
            return
        }
        Log.d(tag, "trying to show in app review")
        setInAppReview()
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(tag, "Review task successful")
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
                    Log.d(tag, "Review flow complete")
                    finishAfterTransition()
                }
            } else {
                Log.e(tag, "Review task failed: ${task.exception?.message}")
                finishAfterTransition()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        finishAfterTransition()
    }
}

fun AppCompatActivity.showInAppReviewIfPossible() {
    try {
        if (canShowInAppReview()) {
            Log.d(tag, "In app review requested less than 14 days ago, skipping")
            return
        }
        Log.d(tag, "trying to show in app review")
        setInAppReview()
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(tag, "Review task successful")
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
                    Log.d(tag, "Review flow complete")
                }
            } else {
                Log.e(tag, "Review task failed: ${task.exception?.message}")
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
