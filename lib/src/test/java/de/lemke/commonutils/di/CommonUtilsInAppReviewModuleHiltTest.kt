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
package de.lemke.commonutils.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.ui.utils.InAppReview
import de.lemke.commonutils.ui.utils.PlayInAppReview
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.TimeUnit.DAYS
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The include an app declares to opt in to [CommonUtilsInAppReviewModule]. */
@Module(includes = [CommonUtilsInAppReviewModule::class])
@InstallIn(SingletonComponent::class)
interface CommonUtilsInAppReviewTestInclude

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class CommonUtilsInAppReviewModuleHiltTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val settings: SettingsRepository = SettingsRepository(freshTestPreferences())

    @Inject
    lateinit var review: InAppReview

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `an app that includes the module gets the Play in-app review`() {
        review.shouldBeInstanceOf<PlayInAppReview>()
    }

    @Test
    fun `the bound review keeps its cooldown in the injected settings`() {
        review.canShow().shouldBeFalse()
        settings.lastInAppReview shouldBeGreaterThan 0L

        settings.lastInAppReview = System.currentTimeMillis() - DAYS.toMillis(15)

        review.canShow().shouldBeTrue()
    }
}
