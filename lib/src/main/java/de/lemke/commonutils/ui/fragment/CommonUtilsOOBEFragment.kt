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

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout.LayoutParams
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialSharedAxis
import de.lemke.commonutils.R
import de.lemke.commonutils.autoCleared
import de.lemke.commonutils.clearLastNestedScrollingChild
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.databinding.FragmentOobeBinding
import dev.oneuiproject.oneui.widget.OnboardingTipsItemView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pre-built onboarding (OOBE) fragment that presents feature tips and a TOS acceptance flow. */
class CommonUtilsOOBEFragment : TransitionFragmentSharedAxis(R.layout.fragment_oobe, MaterialSharedAxis.Y) {
    private val binding by autoCleared { FragmentOobeBinding.bind(requireView()) }

    override fun onDestroyView() {
        // TODO Remove once sesl-androidx CoordinatorLayout uses WeakReference<View> for
        //  mLastNestedScrollingChild (fix tracked in sesl-androidx fix/memory-leaks).
        clearLastNestedScrollingChild()
        super.onDestroyView()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        initTipsItems()
        initToSView()
        initFooterButton()
    }

    private fun initTipsItems() {
        val tipsData =
            listOf(
                Triple(R.string.commonutils_oobe1_title, R.string.commonutils_oobe1_summary, R.drawable.commonutils_oobe1_icon),
                Triple(R.string.commonutils_oobe2_title, R.string.commonutils_oobe2_summary, R.drawable.commonutils_oobe2_icon),
                Triple(R.string.commonutils_oobe3_title, R.string.commonutils_oobe3_summary, R.drawable.commonutils_oobe3_icon),
            )
        tipsData.forEach { (titleRes, summaryRes, iconRes) ->
            OnboardingTipsItemView(requireContext()).apply {
                setIcon(iconRes)
                title = getString(titleRes)
                summary = getString(summaryRes)
                binding.oobeIntroTipsContainer.addView(this, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        }
    }

    private fun initToSView() {
        val tos = getString(R.string.commonutils_tos)
        val tosText =
            getString(
                if (tosChanged) R.string.commonutils_oobe_new_tos_text else R.string.commonutils_oobe_tos_text,
                tos,
            )
        val tosIndex = tosText.lastIndexOf(tos)
        binding.oobeIntroFooterTosText.text =
            SpannableString(tosText).apply {
                setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            AlertDialog
                                .Builder(requireContext())
                                .setTitle(getString(R.string.commonutils_tos))
                                .setMessage(getString(R.string.commonutils_tos_content))
                                .setPositiveButton(R.string.commonutils_ok, null)
                                .show()
                        }
                    },
                    tosIndex,
                    tosIndex + tos.length,
                    SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        binding.oobeIntroFooterTosText.movementMethod = LinkMovementMethod.getInstance()
        binding.oobeIntroFooterTosText.highlightColor = TRANSPARENT
    }

    private fun initFooterButton() {
        val resources = requireContext().resources
        if (resources.configuration.screenWidthDp < MIN_FULL_BUTTON_WIDTH_DP) {
            binding.oobeIntroFooterButton.layoutParams.width = MATCH_PARENT
        }
        binding.oobeIntroFooterButton.setOnClickListener {
            binding.oobeIntroFooterTosText.isEnabled = false
            binding.oobeIntroFooterButton.isVisible = false
            binding.oobeIntroFooterButtonProgress.isVisible = true
            if (setAcceptedTosVersion) {
                commonUtilsSettings.acceptedTosVersion = resources.getInteger(R.integer.commonutils_tos_version)
            }
            viewLifecycleOwner.lifecycleScope.launch {
                delay(PROCEED_DELAY_MS)
                onCompleteNavAction
                    .takeIf { it != 0 }
                    ?.let {
                        findNavController().navigate(
                            it,
                            null,
                            NavOptions
                                .Builder()
                                .setPopUpTo(R.id.commonutils_oobe_dest, inclusive = true)
                                .build(),
                        )
                    }
                    ?: onContinue?.invoke()
            }
        }
    }

    companion object {
        private const val PROCEED_DELAY_MS = 500L
        private const val MIN_FULL_BUTTON_WIDTH_DP = 360

        /** Whether to persist TOS acceptance when the user proceeds. */
        var setAcceptedTosVersion = true

        /** Navigation action ID to navigate to after OOBE completion; mutually exclusive with [onContinue]. */
        var onCompleteNavAction: Int = 0

        /** Callback invoked on OOBE completion when no [onCompleteNavAction] is set. */
        var onContinue: (() -> Unit)? = null

        /** `true` if TOS content changed since the user last accepted; shown as a "new TOS" notice. */
        var tosChanged = false
    }
}
