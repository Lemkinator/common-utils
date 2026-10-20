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
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.data.canShowInAppReview
import javax.inject.Inject

/**
 * Requests the Play in-app review flow for an activity.
 *
 * An app injects it into the screen that asks for a review, so a test swaps in `FakeInAppReview` from the testFixtures.
 */
public interface InAppReview {
    /** `true` once the cooldown period has elapsed. The first call starts the cooldown, so it returns `false` on a fresh install. */
    public fun canShow(): Boolean

    /** Requests the review flow if the cooldown period has elapsed; skips it silently otherwise. */
    public fun showIfPossible(activity: AppCompatActivity)

    /** Requests the review flow if the cooldown period has elapsed; finishes [activity] whether the review is shown or skipped. */
    public fun showOrFinish(activity: AppCompatActivity)
}

/** The [InAppReview] over [showInAppReviewIfPossible] and [showInAppReviewOrFinish], with the cooldown kept in [settings]. */
public class PlayInAppReview @Inject constructor(
    private val settings: SettingsRepository,
) : InAppReview {
    override fun canShow(): Boolean = settings.canShowInAppReview()

    override fun showIfPossible(activity: AppCompatActivity): Unit = activity.showInAppReviewIfPossible(settings)

    override fun showOrFinish(activity: AppCompatActivity): Unit = activity.showInAppReviewOrFinish(settings)
}
