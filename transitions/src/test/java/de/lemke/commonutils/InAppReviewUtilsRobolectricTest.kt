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

import de.lemke.commonutils.data.canShowInAppReview
import de.lemke.commonutils.data.markInAppReviewRequested
import de.lemke.commonutils.ui.utils.FakeInAppReviewCooldown
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.util.concurrent.TimeUnit.DAYS
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InAppReviewUtilsRobolectricTest {
    private val cooldown = FakeInAppReviewCooldown()

    @Test
    fun `lastInAppReview defaults to 0`() {
        cooldown.lastInAppReview shouldBe 0L
    }

    @Test
    fun `canShowInAppReview seeds the timestamp on first call and returns false`() {
        val before = System.currentTimeMillis()
        cooldown.canShowInAppReview().shouldBeFalse()
        cooldown.lastInAppReview shouldBeGreaterThan before - 1
    }

    @Test
    fun `canShowInAppReview returns false when last review was 13 days ago`() {
        cooldown.lastInAppReview = System.currentTimeMillis() - DAYS.toMillis(13)
        cooldown.canShowInAppReview().shouldBeFalse()
    }

    @Test
    fun `canShowInAppReview returns true when last review was 15 days ago`() {
        cooldown.lastInAppReview = System.currentTimeMillis() - DAYS.toMillis(15)
        cooldown.canShowInAppReview().shouldBeTrue()
    }

    @Test
    fun `markInAppReviewRequested restarts the cooldown`() {
        cooldown.lastInAppReview = System.currentTimeMillis() - DAYS.toMillis(15)
        cooldown.markInAppReviewRequested()
        cooldown.canShowInAppReview().shouldBeFalse()
    }
}
