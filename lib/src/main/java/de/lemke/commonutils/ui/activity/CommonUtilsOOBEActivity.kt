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
package de.lemke.commonutils.ui.activity

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.graphics.Color.TRANSPARENT
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import de.lemke.commonutils.R
import de.lemke.commonutils.advanceOnboarding
import de.lemke.commonutils.databinding.ActivityOobeBinding
import dev.oneuiproject.oneui.widget.OnboardingTipsItemView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pre-built onboarding (OOBE) screen that presents feature tips and a TOS acceptance flow. */
class CommonUtilsOOBEActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOobeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SDK_INT >= UPSIDE_DOWN_CAKE) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityOobeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setTitle(applicationInfo.loadLabel(packageManager).toString())
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
            OnboardingTipsItemView(this).apply {
                setIcon(iconRes)
                title = getString(titleRes)
                summary = getString(summaryRes)
                binding.oobeIntroTipsContainer.addView(this, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        }
    }

    private fun initToSView() {
        val tos = getString(R.string.commonutils_tos)
        val tosText = getString(if (tosChanged) R.string.commonutils_oobe_new_tos_text else R.string.commonutils_oobe_tos_text, tos)
        val tosIndex = tosText.lastIndexOf(tos)
        binding.oobeIntroFooterTosText.text =
            SpannableString(tosText).apply {
                setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            AlertDialog
                                .Builder(this@CommonUtilsOOBEActivity)
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
        if (resources.configuration.screenWidthDp < MIN_FULL_BUTTON_WIDTH_DP) {
            binding.oobeIntroFooterButton.layoutParams.width = MATCH_PARENT
        }
        binding.oobeIntroFooterButton.setOnClickListener {
            binding.oobeIntroFooterTosText.isEnabled = false
            binding.oobeIntroFooterButton.isVisible = false
            binding.oobeIntroFooterButtonProgress.isVisible = true
            lifecycleScope.launch {
                delay(PROCEED_DELAY_MS)
                advanceOnboarding()
            }
        }
    }

    companion object {
        private const val PROCEED_DELAY_MS = 500L
        private const val MIN_FULL_BUTTON_WIDTH_DP = 360

        /** `true` if TOS content changed since the user last accepted; shown as a "new TOS" notice. */
        var tosChanged = false
    }
}
