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
    @Axis axis: Int
) : TransitionFragment(
    layoutResId = layoutResId,
    customEnterTransition = MaterialSharedAxis(axis, true),
    customExitTransition = MaterialSharedAxis(axis, true),
    customReenterTransition = MaterialSharedAxis(axis, false),
    customReturnTransition = MaterialSharedAxis(axis, false)
)
