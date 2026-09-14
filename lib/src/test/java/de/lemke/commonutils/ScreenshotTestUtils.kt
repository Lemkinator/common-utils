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

import android.view.ViewGroup
import androidx.core.view.descendants
import com.airbnb.lottie.LottieAnimationView

/**
 * Lottie drives its own Choreographer-based animator, independent of the Looper queue Robolectric
 * drains between actions - an autoplaying/looping animation is captured at whichever frame happened
 * to be current, which shifts across Kotlin/Compose-compiler bumps. Pin every LottieAnimationView to
 * frame 0 before a screenshot capture so it is deterministic.
 */
fun ViewGroup.pinLottieAnimationsToFirstFrame() {
    descendants
        .filterIsInstance<LottieAnimationView>()
        .forEach {
            it.pauseAnimation()
            it.progress = 0f
        }
}
