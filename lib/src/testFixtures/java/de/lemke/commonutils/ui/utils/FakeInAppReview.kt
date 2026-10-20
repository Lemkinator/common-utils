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

/** In-memory [InAppReview] that shows no review: each call records a [Call], and [showOrFinish] finishes the activity. */
public class FakeInAppReview : InAppReview {
    private val recordedCalls = mutableListOf<Call>()

    /** Every call in order. */
    public val calls: List<Call> get() = recordedCalls.toList()

    override fun showIfPossible(activity: AppCompatActivity) {
        recordedCalls += Call.ShowIfPossible(activity)
    }

    override fun showOrFinish(activity: AppCompatActivity) {
        recordedCalls += Call.ShowOrFinish(activity)
        activity.finishAfterTransition()
    }

    /** A recorded call to [FakeInAppReview]. */
    public sealed interface Call {
        /** The activity the call was made for. */
        public val activity: AppCompatActivity

        /** A [FakeInAppReview.showIfPossible] call. */
        public data class ShowIfPossible(
            override val activity: AppCompatActivity,
        ) : Call

        /** A [FakeInAppReview.showOrFinish] call. */
        public data class ShowOrFinish(
            override val activity: AppCompatActivity,
        ) : Call
    }
}
