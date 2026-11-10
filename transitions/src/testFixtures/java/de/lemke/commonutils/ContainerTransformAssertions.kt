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

import android.content.Intent
import de.lemke.commonutils.ui.utils.DEFAULT_DURATION
import de.lemke.commonutils.ui.utils.DEFAULT_FADE_MODE
import de.lemke.commonutils.ui.utils.DURATION_KEY
import de.lemke.commonutils.ui.utils.FADE_MODE_KEY
import de.lemke.commonutils.ui.utils.TRANSITION_NAME_KEY

/**
 * Fails unless this launch intent carries the container transform that `View.transformToActivity` adds for
 * [transitionName], [duration] and [fadeMode].
 */
public fun Intent.shouldBeContainerTransformTo(
    transitionName: String,
    duration: Long = DEFAULT_DURATION,
    fadeMode: Int = DEFAULT_FADE_MODE,
) {
    val actualName = getStringExtra(TRANSITION_NAME_KEY)
    val actualDuration = getLongExtra(DURATION_KEY, 0L).takeIf { hasExtra(DURATION_KEY) }
    val actualFadeMode = getIntExtra(FADE_MODE_KEY, 0).takeIf { hasExtra(FADE_MODE_KEY) }
    if (actualName != transitionName || actualDuration != duration || actualFadeMode != fadeMode) {
        throw AssertionError(
            "Expected a container transform to <$transitionName> lasting $duration ms with fade mode $fadeMode " +
                "but was <$actualName> lasting $actualDuration ms with fade mode $actualFadeMode",
        )
    }
}

/** Fails if this launch intent carries any part of a container transform. */
public fun Intent.shouldHaveNoContainerTransform() {
    val present = listOf(TRANSITION_NAME_KEY, DURATION_KEY, FADE_MODE_KEY).filter(::hasExtra)
    if (present.isNotEmpty()) throw AssertionError("Expected no container transform but the intent carries $present")
}
