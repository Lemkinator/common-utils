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

import androidx.appcompat.app.AppCompatActivity
import de.lemke.commonutils.DestroyActivitiesRule
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.track
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThan
import java.util.concurrent.TimeUnit.DAYS
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InAppReviewTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private lateinit var activity: AppCompatActivity
    private val settings = SettingsRepository(freshTestPreferences())

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
    fun `PlayInAppReview showIfPossible within the cooldown seeds it and leaves the activity open`() {
        PlayInAppReview(settings).showIfPossible(activity)

        settings.lastInAppReview shouldBeGreaterThan 0L
        activity.isFinishing.shouldBeFalse()
    }

    @Test
    fun `PlayInAppReview showOrFinish within the cooldown finishes the activity`() {
        PlayInAppReview(settings).showOrFinish(activity)

        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `PlayInAppReview canShow is false on a fresh install and true after the cooldown`() {
        val review = PlayInAppReview(settings)

        review.canShow().shouldBeFalse()
        settings.lastInAppReview = System.currentTimeMillis() - DAYS.toMillis(15)
        review.canShow().shouldBeTrue()
    }

    @Test
    fun `FakeInAppReview canShow returns its availability and records nothing`() {
        FakeInAppReview().canShow().shouldBeTrue()
        FakeInAppReview(available = false).canShow().shouldBeFalse()
        FakeInAppReview().calls.shouldBeEmpty()
    }

    @Test
    fun `FakeInAppReview showIfPossible records the call and leaves the activity open`() {
        val review = FakeInAppReview()

        review.showIfPossible(activity)

        review.calls shouldContainExactly listOf(FakeInAppReview.Call.ShowIfPossible(activity))
        activity.isFinishing.shouldBeFalse()
    }

    @Test
    fun `FakeInAppReview showOrFinish records the call and finishes the activity`() {
        val review = FakeInAppReview()

        review.showOrFinish(activity)

        review.calls shouldContainExactly listOf(FakeInAppReview.Call.ShowOrFinish(activity))
        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `FakeInAppReview records every call in order`() {
        val review = FakeInAppReview()

        review.showIfPossible(activity)
        review.showOrFinish(activity)

        review.calls shouldContainExactly
            listOf(FakeInAppReview.Call.ShowIfPossible(activity), FakeInAppReview.Call.ShowOrFinish(activity))
    }
}
