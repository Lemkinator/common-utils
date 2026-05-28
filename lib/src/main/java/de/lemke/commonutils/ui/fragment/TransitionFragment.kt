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

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.transition.Transition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.google.android.material.transition.MaterialElevationScale
import com.google.android.material.transition.MaterialSharedAxis
import com.google.android.material.transition.MaterialSharedAxis.Axis
import java.lang.ref.WeakReference

/** Base [Fragment] that wires up shared-element transitions on `onCreate`. */
abstract class TransitionFragment(
    @LayoutRes layoutResId: Int = 0,
    private val customEnterTransition: Transition = MaterialElevationScale(true),
    private val customExitTransition: Transition = MaterialElevationScale(true),
    private val customReenterTransition: Transition = MaterialElevationScale(false),
    private val customReturnTransition: Transition = MaterialElevationScale(false),
) : Fragment(layoutResId) {
    // Saved before animation moves the view into the scene root's overlay, where view.parent
    // would no longer be the FragmentContainerView needed by TransitionManager.endTransitions.
    private var savedContainer: WeakReference<ViewGroup>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = customEnterTransition
        exitTransition = customExitTransition
        reenterTransition = customReenterTransition
        returnTransition = customReturnTransition
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        (view.parent as? ViewGroup)?.let { savedContainer = WeakReference(it) }
    }

    override fun onDestroyView() {
        clearTransitionState()
        super.onDestroyView()
    }

    @Suppress("TooGenericExceptionCaught", "UNCHECKED_CAST", "CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private fun clearTransitionState() {
        val rootView = view

        // (1) End transitions via the correct scene root (FragmentContainerView saved before any
        // overlay move). This clears sRunningTransitions, calls mCurrentAnimators.clear() and
        // animator.end() on each child, and fires onTransitionEnd listeners (which release
        // SpecialEffectsController$Operation → fragment refs via FragmentTransitionSupport$4).
        savedContainer?.get()?.let { TransitionManager.endTransitions(it) }
        savedContainer = null

        // (2) Clear mCurrentAnimators on stored transitions and their TransitionSet children.
        // Transition.end() clears this only when --mNumInstances == 0; if mNumInstances is
        // already wrong (bug), stale ObjectAnimator.mTarget refs keep fragment views alive even
        // though the transition field itself is still held by the (alive) fragment on the back stack.
        try {
            val mCurrentAnimatorsField =
                Transition::class.java
                    .getDeclaredField("mCurrentAnimators")
                    .apply { isAccessible = true }

            fun clearAnimators(t: Transition) {
                @Suppress("UNCHECKED_CAST")
                (mCurrentAnimatorsField.get(t) as? MutableList<Animator>)?.also { list ->
                    list.forEach { clearAnimatorTargetsRecursive(it) }
                    list.clear()
                }
                if (t is TransitionSet) repeat(t.transitionCount) { i -> t.getTransitionAt(i)?.let { clearAnimators(it) } }
            }
            listOf(customEnterTransition, customExitTransition, customReenterTransition, customReturnTransition)
                .forEach { clearAnimators(it) }
        } catch (_: Exception) {
        }

        // (3) Clear mEndValuesList / mStartValuesList — these are NOT cleared by Transition.end(),
        // causing TransitionValues.view to retain fragment views through the outer TransitionSet.
        try {
            val mParentField = Transition::class.java.getDeclaredField("mParent").apply { isAccessible = true }
            val mEndListField = Transition::class.java.getDeclaredField("mEndValuesList").apply { isAccessible = true }
            val mStartListField = Transition::class.java.getDeclaredField("mStartValuesList").apply { isAccessible = true }
            listOf(customEnterTransition, customExitTransition, customReenterTransition, customReturnTransition)
                .forEach { t ->
                    var current: Transition? = t
                    while (current != null) {
                        mEndListField.set(current, null)
                        mStartListField.set(current, null)
                        current = mParentField.get(current) as? Transition
                    }
                }
        } catch (_: Exception) {
        }

        // (4) Belt-and-suspenders: purge any remaining Transition.sRunningAnimators entries that
        // still reference this fragment's views (mView) or transitions (mParent / mCloneParent).
        if (rootView == null) return
        try {
            val mParentField = Transition::class.java.getDeclaredField("mParent").apply { isAccessible = true }
            val mCloneParentField = Transition::class.java.getDeclaredField("mCloneParent").apply { isAccessible = true }
            val sRunningAnimatorsField =
                Transition::class.java
                    .getDeclaredField("sRunningAnimators")
                    .apply { isAccessible = true }
            val runningAnimators =
                (sRunningAnimatorsField.get(null) as? ThreadLocal<MutableMap<Animator, Any>>)
                    ?.get()
                    ?.takeIf { it.isNotEmpty() } ?: return
            val animInfoClass =
                Transition::class.java.declaredClasses
                    .firstOrNull { it.simpleName == "AnimationInfo" } ?: return
            val mViewField = animInfoClass.getDeclaredField("mView").apply { isAccessible = true }
            val mTransitionField = animInfoClass.getDeclaredField("mTransition").apply { isAccessible = true }
            val ourTransitions =
                setOf(
                    customEnterTransition,
                    customExitTransition,
                    customReenterTransition,
                    customReturnTransition,
                )
            val toCancel =
                runningAnimators.entries
                    .filter { (_, info) ->
                        (mViewField.get(info) as? View)?.isInHierarchyOf(rootView) == true ||
                            run {
                                val t0 = mTransitionField.get(info) as? Transition ?: return@run false
                                // Walk the mParent chain (non-cloned path)
                                var t: Transition? = t0
                                while (t != null) {
                                    if (t in ourTransitions) return@run true
                                    t = mParentField.get(t) as? Transition
                                }
                                // Walk mCloneParent (clone → original) then its mParent chain
                                t = mCloneParentField.get(t0) as? Transition
                                while (t != null) {
                                    if (t in ourTransitions) return@run true
                                    t = mParentField.get(t) as? Transition
                                }
                                false
                            }
                    }.map { it.key }
            toCancel.forEach {
                it.cancel()
                runningAnimators.remove(it)
            }
        } catch (_: Exception) {
        }
    }
}

private fun clearAnimatorTargetsRecursive(animator: Animator) {
    when (animator) {
        is ObjectAnimator -> animator.target = null
        is AnimatorSet -> animator.childAnimations.forEach { clearAnimatorTargetsRecursive(it) }
    }
}

// Walk up from this view toward root; returns true if root is an ancestor or the same view.
private fun View.isInHierarchyOf(root: View): Boolean {
    var v: View? = this
    while (v != null) {
        if (v === root) return true
        v = v.parent as? View
    }
    return false
}

/** [TransitionFragment] variant that uses [MaterialSharedAxis] transitions along the given [axis]. */
@Suppress("IncorrectFormatting")
abstract class TransitionFragmentSharedAxis(
    @LayoutRes layoutResId: Int = 0,
    @Axis axis: Int,
) : TransitionFragment(
        layoutResId = layoutResId,
        customEnterTransition = MaterialSharedAxis(axis, true),
        customExitTransition = MaterialSharedAxis(axis, true),
        customReenterTransition = MaterialSharedAxis(axis, false),
        customReturnTransition = MaterialSharedAxis(axis, false),
    )
