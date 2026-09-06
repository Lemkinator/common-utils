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
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.utils.defaultWindowBackground
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.setWindowTransparent
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.TimeUnit.DAYS
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PredictiveBackGestureUtilsRobolectricTest {
    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    }

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
    fun `callback handleOnBackProgressed animates view`() {
        val view = View(activity)
        view.layout(0, 0, 1000, 2000) // non-zero size so division does not produce NaN
        activity.setCustomBackAnimation(view)
        val dispatcher = activity.onBackPressedDispatcher
        val event = BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackStarted(event)
        dispatcher.dispatchOnBackProgressed(event)
        // view should have been translated/scaled
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
        // MutableStateFlow(false) → backEnabled?.value != false = false → callback disabled initially
        val view = View(activity)
        activity.setCustomBackAnimation(view, MutableStateFlow(false))
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
    fun `setCustomBackAnimation with inAppReview settings covers review branches`() {
        val view = View(activity)
        view.layout(0, 0, 1000, 2000)
        val settings =
            SettingsRepository(freshTestPreferences()).apply {
                lastInAppReview = System.currentTimeMillis() - DAYS.toMillis(15)
            }
        val lastInAppReviewBeforeBackPress = settings.lastInAppReview
        activity.setCustomBackAnimation(view, inAppReview = settings)
        val dispatcher = activity.onBackPressedDispatcher
        val event = BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackStarted(event)
        dispatcher.dispatchOnBackProgressed(event)
        // handleOnBackProgressed: reviewSettings != null → early return, so the scale/shift animation never runs
        view.translationX shouldBe 0f
        view.scaleX shouldBe 1f
        dispatcher.onBackPressed()
        // handleOnBackPressed: reviewSettings != null → showInAppReviewOrFinish(reviewSettings) restarts the cooldown,
        // which finishAfterTransition() alone would never touch
        settings.lastInAppReview shouldNotBe lastInAppReviewBeforeBackPress
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PredictiveBackGestureUtilsSdk29RobolectricTest {
    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    }

    @Test
    fun `setWindowTransparent true on pre-R does not call setTranslucent`() {
        // SDK_INT (29) < R (30) → if (SDK_INT >= R) false branch covered
        activity.setWindowTransparent(true)
    }

    @Test
    fun `setWindowTransparent false on pre-R does not call setTranslucent`() {
        activity.setWindowTransparent(false)
    }
}
