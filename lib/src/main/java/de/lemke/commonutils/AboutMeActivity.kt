package de.lemke.commonutils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.os.Build
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import de.lemke.commonutils.databinding.ActivityAboutMeBinding
import dev.oneuiproject.oneui.ktx.isInMultiWindowModeCompat
import dev.oneuiproject.oneui.ktx.semSetToolTipText
import dev.oneuiproject.oneui.widget.AdaptiveCoordinatorLayout.Companion.MARGIN_PROVIDER_ADP_DEFAULT
import kotlin.math.abs

class AboutMeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutMeBinding
    private val appBarListener: AboutAppBarListener = AboutAppBarListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityAboutMeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackPressAnimation(binding.root)
        binding.root.configureAdaptiveMargin(MARGIN_PROVIDER_ADP_DEFAULT, binding.aboutBottomContainer)
        applyInsetIfNeeded()
        setupToolbar()

        initContent()
        refreshAppBar(resources.configuration)
        setupOnClickListeners()
    }

    private fun applyInsetIfNeeded() {
        if (Build.VERSION.SDK_INT >= 30 && !window.decorView.fitsSystemWindows) {
            binding.root.setOnApplyWindowInsetsListener { _, insets ->
                val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                binding.root.setPadding(
                    systemBarsInsets.left, systemBarsInsets.top,
                    systemBarsInsets.right, systemBarsInsets.bottom
                )
                insets
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.aboutToolbar)
        //Should be called after setSupportActionBar
        binding.aboutToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun refreshAppBar(config: Configuration) {
        if (config.orientation != ORIENTATION_LANDSCAPE && !isInMultiWindowModeCompat) {
            binding.aboutAppBar.apply {
                seslSetCustomHeightProportion(true, 0.5f)//expanded
                addOnOffsetChangedListener(appBarListener)
                setExpanded(true, false)
            }
            binding.aboutSwipeUpContainer.apply {
                updateLayoutParams { height = resources.displayMetrics.heightPixels / 2 }
                visibility = VISIBLE
            }
        } else {
            binding.aboutAppBar.apply {
                setExpanded(false, false)
                seslSetCustomHeightProportion(true, 0f)
                removeOnOffsetChangedListener(appBarListener)
            }
            binding.aboutBottomContainer.alpha = 1f
            binding.aboutSwipeUpContainer.visibility = GONE
            setBottomContentEnabled(true)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshAppBar(newConfig)
    }

    private fun initContent() {
        val icon = AppCompatResources.getDrawable(this, R.drawable.me4_round)
        binding.aboutHeaderIcon.setImageDrawable(icon)
        binding.aboutBottomIcon.setImageDrawable(icon)
        binding.aboutHeaderGithub.semSetToolTipText(getString(R.string.github))
        binding.aboutHeaderPlayStore.semSetToolTipText(getString(R.string.playstore))
        binding.aboutHeaderWebsite.semSetToolTipText(getString(R.string.website))
        binding.aboutHeaderInsta.semSetToolTipText(getString(R.string.instagram))
        binding.aboutHeaderTiktok.semSetToolTipText(getString(R.string.tiktok))
    }

    private fun setBottomContentEnabled(enabled: Boolean) {
        binding.aboutHeaderGithub.isEnabled = !enabled
        binding.aboutHeaderWebsite.isEnabled = !enabled
        binding.aboutHeaderPlayStore.isEnabled = !enabled
        binding.aboutHeaderInsta.isEnabled = !enabled
        binding.aboutHeaderTiktok.isEnabled = !enabled
        binding.aboutBottomContent.aboutBottomRateApp.isEnabled = enabled
        binding.aboutBottomContent.aboutBottomShareApp.isEnabled = enabled
        binding.aboutBottomContent.aboutBottomWriteEmail.isEnabled = enabled
        binding.aboutBottomContent.aboutBottomRelativeTiktok.isEnabled = enabled
        binding.aboutBottomContent.aboutBottomRelativeWebsite.isEnabled = enabled
        binding.aboutBottomContent.aboutBottomRelativePlayStore.isEnabled = enabled
    }

    private fun openURL(url: String) = openURL(url, cantOpenURLMessage, noBrowserInstalledMessage)

    private fun openPlayStore() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.playstore_ad))
            .setMessage(getString(R.string.playstore_redirect_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                openURL(getString(R.string.playstore_developer_page_link))
            }
            .setNegativeButton(getString(R.string.sesl_cancel), null)
            .show()
    }

    private fun setupOnClickListeners() {
        binding.aboutHeaderGithub.setOnClickListener { openURL(getString(R.string.my_github)) }
        binding.aboutHeaderPlayStore.setOnClickListener { openPlayStore() }
        binding.aboutHeaderWebsite.setOnClickListener { openURL(getString(R.string.my_website)) }
        binding.aboutHeaderInsta.setOnClickListener { openURL(getString(R.string.my_insta)) }
        binding.aboutHeaderTiktok.setOnClickListener { openURL(getString(R.string.rick_roll_troll_link)) }
        with(binding.aboutBottomContent) {
            aboutBottomRelativePlayStore.setOnClickListener { openPlayStore() }
            aboutBottomRelativeWebsite.setOnClickListener { openURL(getString(R.string.my_website)) }
            aboutBottomRelativeTiktok.setOnClickListener { openURL(getString(R.string.rick_roll_troll_link)) }
            aboutBottomRateApp.setOnClickListener { openApp(packageName, false) }
            aboutBottomShareApp.setOnClickListener {
                onShareApp(this@AboutMeActivity)
                shareApp()
            }
            aboutBottomWriteEmail.setOnClickListener { sendEmailAboutMe(email, appName, noEmailAppInstalledText) }
        }
    }

    private inner class AboutAppBarListener : OnOffsetChangedListener {
        override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
            // Handle the SwipeUp anim view
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
            // Handle the bottom part of the UI
            val alphaRange = binding.aboutCTL.height * 0.143f
            val layoutPosition = abs(appBarLayout.top).toFloat()
            val bottomAlpha = (150.0f / alphaRange * (layoutPosition - binding.aboutCTL.height * 0.35f)).coerceIn(0f, 255f)
            binding.aboutBottomContainer.alpha = bottomAlpha / 255
        }
    }

    companion object {
        var email = "apps@leonard-lemke.com"
        var appName = "App"
        var onShareApp: (activity: Activity) -> Unit = {}
        var cantOpenURLMessage: String? = null
        var noBrowserInstalledMessage: String? = null
        var noEmailAppInstalledText: String? = null
    }
}