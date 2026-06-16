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
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class PredictiveBackGestureUtilsRobolectricTest {
    private lateinit var activity: AppCompatActivity

    @BeforeEach
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
        view.translationX shouldNotBe null
        view.scaleX shouldNotBe null
    }

    @Test
    fun `callback handleOnBackPressed finishes activity`() {
        val view = View(activity)
        activity.setCustomBackAnimation(view)
        activity.onBackPressedDispatcher.onBackPressed()
        activity.isFinishing.shouldBeTrue()
    }
}
