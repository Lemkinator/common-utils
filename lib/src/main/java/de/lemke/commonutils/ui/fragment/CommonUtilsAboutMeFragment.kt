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

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import com.google.android.material.transition.MaterialSharedAxis
import de.lemke.commonutils.R
import de.lemke.commonutils.autoCleared
import de.lemke.commonutils.clearLastNestedScrollingChild
import de.lemke.commonutils.databinding.FragmentAboutMeBinding
import de.lemke.commonutils.openApp
import de.lemke.commonutils.openURL
import de.lemke.commonutils.sendEmailAboutMe
import de.lemke.commonutils.shareApp
import dev.oneuiproject.oneui.ktx.isInMultiWindowModeCompat
import dev.oneuiproject.oneui.ktx.semSetToolTipText
import dev.oneuiproject.oneui.ktx.setEnableRecursive
import dev.oneuiproject.oneui.widget.AdaptiveCoordinatorLayout.Companion.MARGIN_PROVIDER_ADP_DEFAULT
import kotlin.math.abs
import dev.oneuiproject.oneui.design.R as designR

/** Pre-built About Me fragment that presents developer info and an optional share-app action. */
class CommonUtilsAboutMeFragment : TransitionFragmentSharedAxis(R.layout.fragment_about_me, MaterialSharedAxis.Y) {
    private val binding by autoCleared { FragmentAboutMeBinding.bind(requireView()) }
    private val appBarListener = AboutAppBarListener()
    private val progressInterpolator = PathInterpolatorCompat.create(0f, 0f, 0f, 1f)
    private var isExpanding = false

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
        binding.root.configureAdaptiveMargin(MARGIN_PROVIDER_ADP_DEFAULT, binding.aboutBottomContainer)
        binding.aboutToolbar.visibility = View.GONE
        initContent()
        refreshAppBar(resources.configuration)
        setupOnClickListeners()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshAppBar(newConfig)
    }

    @SuppressLint("RestrictedApi")
    private fun refreshAppBar(config: Configuration) {
        if (config.orientation != ORIENTATION_LANDSCAPE && !requireActivity().isInMultiWindowModeCompat) {
            binding.aboutAppBar.apply {
                seslSetCustomHeightProportion(true, 0.5f)
                addOnOffsetChangedListener(appBarListener)
                setExpanded(true, false)
            }
            binding.aboutSwipeUpContainer.apply {
                updateLayoutParams { height = resources.displayMetrics.heightPixels / 2 }
                isVisible = true
            }
        } else {
            binding.aboutAppBar.apply {
                setExpanded(false, false)
                seslSetCustomHeightProportion(true, 0f)
                removeOnOffsetChangedListener(appBarListener)
            }
            binding.aboutBottomContainer.alpha = 1f
            binding.aboutSwipeUpContainer.isVisible = false
            setBottomContentEnabled(true)
        }
    }

    private fun initContent() {
        val icon = AppCompatResources.getDrawable(requireContext(), R.drawable.me4_round)
        binding.aboutHeaderIcon.setImageDrawable(icon)
        binding.aboutBottomIcon.setImageDrawable(icon)
        binding.aboutHeaderGithub.semSetToolTipText(getString(R.string.commonutils_github))
        binding.aboutHeaderPlayStore.semSetToolTipText(getString(R.string.commonutils_playstore))
        binding.aboutHeaderWebsite.semSetToolTipText(getString(R.string.commonutils_website))
        binding.aboutHeaderInsta.semSetToolTipText(getString(R.string.commonutils_instagram))
        binding.aboutHeaderTiktok.semSetToolTipText(getString(R.string.commonutils_tiktok))
    }

    private fun setBottomContentEnabled(enabled: Boolean) {
        binding.aboutHeaderIcons.setEnableRecursive(!enabled)
        binding.aboutBottomContent.aboutBottomScrollView.setEnableRecursive(enabled)
    }

    private fun openPlayStore() {
        AlertDialog
            .Builder(requireContext())
            .setTitle(getString(R.string.commonutils_playstore_ad))
            .setMessage(getString(R.string.commonutils_playstore_redirect_message))
            .setPositiveButton(getString(R.string.commonutils_yes)) { _, _ ->
                openURL(getString(R.string.commonutils_playstore_developer_page_link))
            }.setNegativeButton(getString(designR.string.oui_des_common_cancel), null)
            .show()
    }

    private fun setupOnClickListeners() {
        binding.aboutHeaderGithub.setOnClickListener { openURL(getString(R.string.commonutils_my_github)) }
        binding.aboutHeaderPlayStore.setOnClickListener { openPlayStore() }
        binding.aboutHeaderWebsite.setOnClickListener { openURL(getString(R.string.commonutils_my_website)) }
        binding.aboutHeaderInsta.setOnClickListener { openURL(getString(R.string.commonutils_my_insta)) }
        binding.aboutHeaderTiktok.setOnClickListener { openURL(getString(R.string.commonutils_rick_roll_troll_link)) }
        with(binding.aboutBottomContent) {
            aboutBottomRelativePlayStore.setOnClickListener { openPlayStore() }
            aboutBottomRelativeWebsite.setOnClickListener { openURL(getString(R.string.commonutils_my_website)) }
            aboutBottomRelativeTiktok.setOnClickListener { openURL(getString(R.string.commonutils_rick_roll_troll_link)) }
            aboutBottomRateApp.setOnClickListener { openApp(requireContext().packageName, false) }
            aboutBottomShareApp.setOnClickListener {
                onShareApp(requireActivity())
                shareApp()
            }
            aboutBottomWriteEmail.setOnClickListener {
                requireContext().sendEmailAboutMe(
                    getString(R.string.commonutils_email),
                    requireContext().applicationInfo.loadLabel(requireContext().packageManager).toString(),
                )
            }
        }
    }

    @Suppress("MagicNumber")
    private inner class AboutAppBarListener : OnOffsetChangedListener {
        override fun onOffsetChanged(
            appBarLayout: AppBarLayout,
            verticalOffset: Int,
        ) {
            val totalScrollRange = appBarLayout.totalScrollRange
            val abs = abs(verticalOffset)
            if (abs >= totalScrollRange / 2) {
                binding.aboutSwipeUpContainer.alpha = 0f
                setBottomContentEnabled(true)
            } else if (abs == 0) {
                binding.aboutSwipeUpContainer.alpha = 1f
                setBottomContentEnabled(false)
            } else {
                val offsetAlpha = appBarLayout.y / totalScrollRange
                binding.aboutSwipeUpContainer.alpha = (1 - offsetAlpha * -3).coerceIn(0f, 1f)
            }
            val alphaRange = binding.aboutCTL.height * 0.143f
            val layoutPosition = abs(appBarLayout.top).toFloat()
            val bottomAlpha = (150.0f / alphaRange * (layoutPosition - binding.aboutCTL.height * 0.35f)).coerceIn(0f, 255f)
            binding.aboutBottomContainer.alpha = bottomAlpha / 255
            val isCollapsed = appBarLayout.getTotalScrollRange() + verticalOffset == 0
            if (!isCollapsed && isExpanding) {
                isExpanding = false
            }
        }
    }

    companion object {
        /** Optional callback invoked when the user taps the share button. */
        var onShareApp: (activity: android.app.Activity) -> Unit = {}
    }
}
