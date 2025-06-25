package de.lemke.commonutils.ui.activity

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Build.VERSION.SDK_INT
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
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.databinding.ActivityOobeBinding
import dev.oneuiproject.oneui.widget.OnboardingTipsItemView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CommonUtilsOOBEActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOobeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityOobeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initTipsItems()
        initToSView()
        initFooterButton()
    }

    private fun initTipsItems() {
        val tipsData = listOf(
            Triple(R.string.commonutils_oobe1_title, R.string.commonutils_oobe1_summary, R.drawable.oobe1_icon),
            Triple(R.string.commonutils_oobe2_title, R.string.commonutils_oobe2_summary, R.drawable.oobe1_icon),
            Triple(R.string.commonutils_oobe3_title, R.string.commonutils_oobe3_summary, R.drawable.oobe1_icon)
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
        val tosText = getString(R.string.commonutils_oobe_tos_text, tos)
        val tosIndex = tosText.indexOf(tos)
        binding.oobeIntroFooterTosText.text = SpannableString(tosText).apply {
            setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        AlertDialog.Builder(this@CommonUtilsOOBEActivity)
                            .setTitle(getString(R.string.commonutils_tos))
                            .setMessage(getString(R.string.commonutils_tos_content))
                            .setPositiveButton(R.string.commonutils_ok, null)
                            .show()
                    }
                },
                tosIndex, tosIndex + tos.length,
                SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.oobeIntroFooterTosText.movementMethod = LinkMovementMethod.getInstance()
        binding.oobeIntroFooterTosText.highlightColor = TRANSPARENT
    }

    private fun initFooterButton() {
        if (resources.configuration.screenWidthDp < 360) binding.oobeIntroFooterButton.layoutParams.width = MATCH_PARENT
        binding.oobeIntroFooterButton.setOnClickListener {
            binding.oobeIntroFooterTosText.isEnabled = false
            binding.oobeIntroFooterButton.isVisible = false
            binding.oobeIntroFooterButtonProgress.isVisible = true
            if (setAcceptedTosVersion) commonUtilsSettings.acceptedTosVersion = resources.getInteger(R.integer.tosVersion)
            lifecycleScope.launch {
                delay(500)
                nextActivity?.let {
                    startActivity(Intent(this@CommonUtilsOOBEActivity, it))
                    @Suppress("DEPRECATION") if (SDK_INT < 34) overridePendingTransition(fade_in, fade_out)
                    finishAfterTransition()
                } ?: onContinue?.invoke() ?: finishAfterTransition()
            }
        }
    }

    companion object {
        var setAcceptedTosVersion = true
        var nextActivity: Class<*>? = null
        var onContinue: (() -> Unit)? = null
    }
}