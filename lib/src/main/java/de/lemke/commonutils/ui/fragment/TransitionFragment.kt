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
package de.lemke.commonutils.ui.fragment

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.transition.Transition
import com.google.android.material.transition.MaterialElevationScale
import com.google.android.material.transition.MaterialSharedAxis
import com.google.android.material.transition.MaterialSharedAxis.Axis

abstract class TransitionFragment(
    @LayoutRes layoutResId: Int,
    private val customEnterTransition: Transition = MaterialElevationScale(true),
    private val customExitTransition: Transition = MaterialElevationScale(true),
    private val customReenterTransition: Transition = MaterialElevationScale(false),
    private val customReturnTransition: Transition = MaterialElevationScale(false),
) : Fragment(layoutResId) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentTransitions()
    }

    private fun setupFragmentTransitions() {
        enterTransition = customEnterTransition
        exitTransition = customExitTransition
        reenterTransition = customReenterTransition
        returnTransition = customReturnTransition
    }
}

abstract class TransitionFragmentSharedAxis(
    @LayoutRes layoutResId: Int,
    @Axis axis: Int,
) : TransitionFragment(
        layoutResId = layoutResId,
        customEnterTransition = MaterialSharedAxis(axis, true),
        customExitTransition = MaterialSharedAxis(axis, true),
        customReenterTransition = MaterialSharedAxis(axis, false),
        customReturnTransition = MaterialSharedAxis(axis, false),
    )
