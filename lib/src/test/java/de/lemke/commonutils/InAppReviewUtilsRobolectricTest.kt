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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.util.concurrent.TimeUnit.DAYS
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class InAppReviewUtilsRobolectricTest {
    private fun setupActivity(): AppCompatActivity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()

    @Test
    fun `getLastInAppReview records and returns current time on first call when no timestamp is stored`() {
        val activity = setupActivity()
        val before = System.currentTimeMillis()
        val first = activity.getLastInAppReview()
        val after = System.currentTimeMillis()
        (first in before..after).shouldBeTrue()
        // Second call returns the stored value, not a fresh currentTimeMillis()
        val second = activity.getLastInAppReview()
        second shouldBe first
    }

    @Test
    fun `setInAppReview stores current time and getLastInAppReview reflects it`() {
        val activity = setupActivity()
        val before = System.currentTimeMillis()
        activity.setInAppReview()
        val result = activity.getLastInAppReview()
        val after = System.currentTimeMillis()
        (result in before..after).shouldBeTrue()
    }

    @Test
    fun `canShowInAppReview returns false immediately after setInAppReview`() {
        val activity = setupActivity()
        activity.setInAppReview()
        activity.canShowInAppReview().shouldBeFalse()
    }

    @Test
    fun `canShowInAppReview returns false when last review was 13 days ago`() {
        val activity = setupActivity()
        val thirteenDaysAgo = System.currentTimeMillis() - DAYS.toMillis(13)
        activity.getSharedPreferences("InAppReviewUtils", MODE_PRIVATE).edit { putLong("lastInAppReview", thirteenDaysAgo) }
        activity.canShowInAppReview().shouldBeFalse()
    }

    @Test
    fun `canShowInAppReview returns true when last review was 15 days ago`() {
        val activity = setupActivity()
        val fifteenDaysAgo = System.currentTimeMillis() - DAYS.toMillis(15)
        activity.getSharedPreferences("InAppReviewUtils", MODE_PRIVATE).edit { putLong("lastInAppReview", fifteenDaysAgo) }
        activity.canShowInAppReview().shouldBeTrue()
    }

    @Test
    fun `canShowInAppReview returns false on fresh activity with no stored timestamp`() {
        val activity = setupActivity()
        // First call records currentTimeMillis() as install timestamp → 0 days elapsed → false
        activity.canShowInAppReview().shouldBeFalse()
    }
}
