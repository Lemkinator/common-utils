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
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import de.lemke.commonutils.data.canShowInAppReview
import de.lemke.commonutils.ui.utils.FakeInAppReviewCooldown
import de.lemke.commonutils.ui.utils.showInAppReviewIfPossible
import de.lemke.commonutils.ui.utils.showInAppReviewOrFinish
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.TimeUnit.DAYS
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShowInAppReviewRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private val reviewInfo = mockk<ReviewInfo>()
    private val manager = mockk<ReviewManager>()
    private val allowed = FakeInAppReviewCooldown().apply { lastInAppReview = System.currentTimeMillis() - DAYS.toMillis(15) }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `showInAppReviewOrFinish finishes without a request while the cooldown runs`() {
        val activity = activity()
        mockkStatic(ReviewManagerFactory::class)

        activity.showInAppReviewOrFinish(FakeInAppReviewCooldown())

        activity.isFinishing.shouldBeTrue()
        verify(exactly = 0) { ReviewManagerFactory.create(any()) }
    }

    @Test
    fun `showInAppReviewOrFinish finishes after the review flow completes and restarts the cooldown`() {
        val activity = activity()
        stubFlow(requestFlow = Tasks.forResult(reviewInfo))

        activity.showInAppReviewOrFinish(allowed)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { manager.launchReviewFlow(activity, reviewInfo) }
        activity.isFinishing.shouldBeTrue()
        allowed.canShowInAppReview().shouldBeFalse()
    }

    @Test
    fun `showInAppReviewOrFinish finishes when the review flow request fails`() {
        val activity = activity()
        stubFlow(requestFlow = Tasks.forException(IllegalStateException("no play")))

        activity.showInAppReviewOrFinish(allowed)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { manager.launchReviewFlow(any(), any()) }
        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `showInAppReviewOrFinish finishes when the review flow request is canceled`() {
        val activity = activity()
        stubFlow(requestFlow = Tasks.forCanceled())

        activity.showInAppReviewOrFinish(allowed)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { manager.launchReviewFlow(any(), any()) }
        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `showInAppReviewOrFinish finishes when the review manager cannot be created`() {
        val activity = activity()
        mockkStatic(ReviewManagerFactory::class)
        every { ReviewManagerFactory.create(any()) } throws IllegalStateException("no play")

        activity.showInAppReviewOrFinish(allowed)

        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `showInAppReviewIfPossible launches the flow and keeps the activity open`() {
        val activity = activity()
        stubFlow(requestFlow = Tasks.forResult(reviewInfo))

        activity.showInAppReviewIfPossible(allowed)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { manager.launchReviewFlow(activity, reviewInfo) }
        activity.isFinishing.shouldBeFalse()
    }

    @Test
    fun `showInAppReviewIfPossible does nothing while the cooldown runs`() {
        val activity = activity()
        mockkStatic(ReviewManagerFactory::class)

        activity.showInAppReviewIfPossible(FakeInAppReviewCooldown())

        verify(exactly = 0) { ReviewManagerFactory.create(any()) }
        activity.isFinishing.shouldBeFalse()
    }

    private fun stubFlow(requestFlow: Task<ReviewInfo>) {
        every { manager.requestReviewFlow() } returns requestFlow
        every { manager.launchReviewFlow(any(), any()) } returns Tasks.forResult(null)
        mockkStatic(ReviewManagerFactory::class)
        every { ReviewManagerFactory.create(any()) } returns manager
    }

    private fun activity(): AppCompatActivity =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .setup()
            .track(destroyActivities)
            .get()
}
