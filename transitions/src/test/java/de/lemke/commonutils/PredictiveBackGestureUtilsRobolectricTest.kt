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

import android.view.View
import androidx.activity.BackEventCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.reflect.SeslBaseReflector
import de.lemke.commonutils.data.InAppReviewCooldown
import de.lemke.commonutils.ui.utils.BackAnimationOutlineProvider
import de.lemke.commonutils.ui.utils.FakeInAppReview
import de.lemke.commonutils.ui.utils.FakeInAppReviewCooldown
import de.lemke.commonutils.ui.utils.defaultWindowBackground
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.setWindowTransparent
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.concurrent.TimeUnit.DAYS
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PredictiveBackGestureUtilsRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        activity =
            Robolectric
                .buildActivity(AppCompatActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
    }

    private fun laidOutView() = View(activity).apply { layout(0, 0, 1000, 2000) }

    @After
    fun tearDown() {
        unmockkStatic(SeslBaseReflector::class)
    }

    private fun reportPopOver(isPopOver: Boolean) {
        mockkStatic(SeslBaseReflector::class)
        every { SeslBaseReflector.invoke(any(), any()) } returns isPopOver
    }

    private fun View.radius() = (outlineProvider as BackAnimationOutlineProvider).radius

    private fun swipe(
        progress: Float,
        touchY: Float = 500f,
    ) = BackEventCompat(10f, touchY, progress, BackEventCompat.EDGE_LEFT)

    @Test
    fun `setWindowTransparent true does not throw`() {
        activity.setWindowTransparent(true)
    }

    @Test
    fun `setWindowTransparent false does not throw`() {
        activity.setWindowTransparent(false)
    }

    @Test
    fun `defaultWindowBackground returns a valid resource id`() {
        activity.defaultWindowBackground shouldNotBe 0
    }

    @Test
    fun `setCustomBackAnimation registers back callback without crashing`() {
        val view = View(activity)
        activity.setCustomBackAnimation(view)
    }

    @Test
    fun `setCustomBackAnimation with backEnabled flow registers back callback`() {
        val view = View(activity)
        val backEnabled = MutableStateFlow(true)
        activity.setCustomBackAnimation(view, backEnabled)
    }

    @Test
    fun `callback handleOnBackProgressed animates view relative to the first touch`() {
        val view = View(activity)
        view.layout(0, 0, 1000, 2000) // non-zero size so division does not produce NaN
        activity.setCustomBackAnimation(view)
        val dispatcher = activity.onBackPressedDispatcher
        val event = BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackStarted(event)

        dispatcher.dispatchOnBackProgressed(event)
        view.translationX shouldBeGreaterThan 0f
        view.translationY shouldBe 0f
        view.scaleX shouldBeLessThan 1f

        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 900f, 0.5f, BackEventCompat.EDGE_LEFT))
        view.translationY shouldBeGreaterThan 0f
    }

    @Test
    fun `callback handleOnBackCancelled resets view`() {
        val view = View(activity)
        view.layout(0, 0, 1000, 2000)
        activity.setCustomBackAnimation(view)
        val dispatcher = activity.onBackPressedDispatcher
        val event = BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackStarted(event)
        dispatcher.dispatchOnBackProgressed(event)
        dispatcher.dispatchOnBackCancelled()
        view.translationX shouldBe 0f
        view.scaleX shouldBe 1f
        view.scaleY shouldBe 1f
    }

    @Test
    fun `callback handleOnBackPressed finishes activity`() {
        val view = View(activity)
        activity.setCustomBackAnimation(view)
        activity.onBackPressedDispatcher.onBackPressed()
        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `setCustomBackAnimation with disabled backEnabled covers value-false branch`() {
        val view = View(activity)
        activity.setCustomBackAnimation(view, MutableStateFlow(false))
        activity.onBackPressedDispatcher.hasEnabledCallbacks().shouldBeFalse()
    }

    @Test
    fun `callback handleOnBackProgressed with EDGE_RIGHT covers swipeEdge else branch`() {
        val view = View(activity)
        view.layout(0, 0, 1000, 2000)
        activity.setCustomBackAnimation(view)
        val dispatcher = activity.onBackPressedDispatcher
        val event = BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_RIGHT)
        dispatcher.dispatchOnBackStarted(event)
        dispatcher.dispatchOnBackProgressed(event)
    }

    @Test
    fun `setCustomBackAnimation with an unavailable InAppReview finishes the activity without asking it`() {
        val view = View(activity)
        val review = FakeInAppReview(available = false)
        activity.setCustomBackAnimation(view, inAppReview = review)
        activity.onBackPressedDispatcher.onBackPressed()
        activity.isFinishing.shouldBeTrue()
        review.calls.shouldBeEmpty()
    }

    @Test
    fun `setCustomBackAnimation with an available InAppReview skips the animation and hands back to it`() {
        val view = View(activity)
        view.layout(0, 0, 1000, 2000)
        val review = FakeInAppReview()
        activity.setCustomBackAnimation(view, inAppReview = review)
        val dispatcher = activity.onBackPressedDispatcher
        val event = BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackStarted(event)
        dispatcher.dispatchOnBackProgressed(event)
        view.translationX shouldBe 0f
        view.scaleX shouldBe 1f
        dispatcher.onBackPressed()
        review.calls shouldContainExactly listOf(FakeInAppReview.Call.ShowOrFinish(activity))
    }

    @Test
    fun `deprecated setCustomBackAnimation with a cooldown restarts the cooldown on back`() {
        val view = View(activity)
        val cooldown = FakeInAppReviewCooldown(System.currentTimeMillis() - DAYS.toMillis(15))
        val lastInAppReviewBeforeBackPress = cooldown.lastInAppReview
        activity.setCustomBackAnimation(view, inAppReview = cooldown)
        activity.onBackPressedDispatcher.onBackPressed()
        cooldown.lastInAppReview shouldNotBe lastInAppReviewBeforeBackPress
    }

    @Test
    fun `deprecated setCustomBackAnimation with a null cooldown finishes the activity`() {
        activity.setCustomBackAnimation(View(activity), inAppReview = null as InAppReviewCooldown?)
        activity.onBackPressedDispatcher.onBackPressed()
        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `a full-screen window shrinks the view with the gesture`() {
        val view = laidOutView()
        activity.setCustomBackAnimation(view)
        val back = activity.onBackPressedDispatcher
        back.dispatchOnBackStarted(swipe(0f))
        back.dispatchOnBackProgressed(swipe(1f))
        view.scaleX shouldBe 0.9f
        view.scaleY shouldBe 0.9f
        view.translationX shouldBe 29f
        view.radius() shouldBe 100f
    }

    @Test
    fun `a pop-over leaves the view unchanged during the gesture`() {
        reportPopOver(true)
        val view = laidOutView()
        activity.setCustomBackAnimation(view)
        val back = activity.onBackPressedDispatcher
        back.dispatchOnBackStarted(swipe(0f))
        back.dispatchOnBackProgressed(swipe(0.5f))
        back.dispatchOnBackProgressed(swipe(1f, touchY = 900f))
        view.scaleX shouldBe 1f
        view.scaleY shouldBe 1f
        view.translationX shouldBe 0f
        view.translationY shouldBe 0f
        view.radius() shouldBe 0f
    }

    @Test
    fun `a pop-over keeps the view unchanged after progress and after a cancel`() {
        reportPopOver(true)
        val view = laidOutView()
        activity.setCustomBackAnimation(view)
        val back = activity.onBackPressedDispatcher
        back.dispatchOnBackStarted(swipe(0f))
        back.dispatchOnBackProgressed(swipe(1f))
        view.scaleX shouldBe 1f
        view.translationX shouldBe 0f
        view.radius() shouldBe 0f
        back.dispatchOnBackCancelled()
        view.scaleX shouldBe 1f
        view.translationX shouldBe 0f
        view.radius() shouldBe 0f
        activity.isFinishing.shouldBeFalse()
    }

    @Test
    fun `back in a pop-over finishes the activity`() {
        reportPopOver(true)
        activity.setCustomBackAnimation(laidOutView())
        activity.onBackPressedDispatcher.onBackPressed()
        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `back in a pop-over with an available review hands back to the review`() {
        reportPopOver(true)
        val review = FakeInAppReview()
        activity.setCustomBackAnimation(laidOutView(), inAppReview = review)
        activity.onBackPressedDispatcher.onBackPressed()
        review.calls shouldContainExactly listOf(FakeInAppReview.Call.ShowOrFinish(activity))
    }

    @Test
    fun `a window that turns into a pop-over stops the preview at the next gesture`() {
        val view = laidOutView()
        reportPopOver(false)
        activity.setCustomBackAnimation(view)
        val back = activity.onBackPressedDispatcher
        back.dispatchOnBackStarted(swipe(0f))
        back.dispatchOnBackProgressed(swipe(1f))
        view.scaleX shouldBe 0.9f
        view.translationX shouldBe 29f
        view.radius() shouldBe 100f
        back.dispatchOnBackCancelled()
        view.scaleX shouldBe 1f
        view.radius() shouldBe 0f
        reportPopOver(true)
        back.dispatchOnBackStarted(swipe(0f))
        back.dispatchOnBackProgressed(swipe(1f))
        view.scaleX shouldBe 1f
        view.translationX shouldBe 0f
        view.radius() shouldBe 0f
    }

    @Test
    fun `a pop-over that turns full screen shrinks again at the next gesture`() {
        val view = laidOutView()
        reportPopOver(true)
        activity.setCustomBackAnimation(view)
        val back = activity.onBackPressedDispatcher
        back.dispatchOnBackStarted(swipe(0f))
        back.dispatchOnBackProgressed(swipe(1f))
        view.scaleX shouldBe 1f
        view.translationX shouldBe 0f
        view.radius() shouldBe 0f
        back.dispatchOnBackCancelled()
        reportPopOver(false)
        back.dispatchOnBackStarted(swipe(0f))
        back.dispatchOnBackProgressed(swipe(1f))
        view.scaleX shouldBe 0.9f
        view.scaleY shouldBe 0.9f
        view.translationX shouldBe 29f
        view.radius() shouldBe 100f
    }

    @Test
    fun `cancel resets the corner rounding of a full-screen window`() {
        val view = laidOutView()
        activity.setCustomBackAnimation(view)
        val back = activity.onBackPressedDispatcher
        back.dispatchOnBackStarted(swipe(0f))
        back.dispatchOnBackProgressed(swipe(1f))
        view.radius() shouldBe 100f
        back.dispatchOnBackCancelled()
        view.radius() shouldBe 0f
        view.translationY shouldBe 0f
        view.scaleY shouldBe 1f
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PredictiveBackGestureUtilsSdk29RobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        activity =
            Robolectric
                .buildActivity(AppCompatActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
    }

    @Test
    fun `setWindowTransparent true on pre-R does not call setTranslucent`() {
        activity.setWindowTransparent(true)
    }

    @Test
    fun `setWindowTransparent false on pre-R does not call setTranslucent`() {
        activity.setWindowTransparent(false)
    }
}
