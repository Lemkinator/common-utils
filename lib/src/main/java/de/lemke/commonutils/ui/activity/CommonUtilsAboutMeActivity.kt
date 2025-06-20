package de.lemke.commonutils.ui.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import de.lemke.commonutils.R
import de.lemke.commonutils.databinding.ActivityAboutMeBinding
import de.lemke.commonutils.openApp
import de.lemke.commonutils.openURL
import de.lemke.commonutils.prepareActivityTransformationTo
import de.lemke.commonutils.sendEmailAboutMe
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.commonutils.shareApp
import dev.oneuiproject.oneui.ktx.invokeOnBack
import dev.oneuiproject.oneui.ktx.isInMultiWindowModeCompat
import dev.oneuiproject.oneui.ktx.semSetToolTipText
import dev.oneuiproject.oneui.ktx.setEnableRecursive
import dev.oneuiproject.oneui.utils.DeviceLayoutUtil.isPortrait
import dev.oneuiproject.oneui.widget.AdaptiveCoordinatorLayout.Companion.MARGIN_PROVIDER_ADP_DEFAULT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import dev.oneuiproject.oneui.design.R as designR

class CommonUtilsAboutMeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutMeBinding
    private val appBarListener: AboutAppBarListener = AboutAppBarListener()
    private val progressInterpolator = PathInterpolatorCompat.create(0f, 0f, 0f, 1f)
    private val callbackIsActive = MutableStateFlow(false)
    private val crossActivityCallbackIsActive = MutableStateFlow(true)
    private var isBackProgressing = false
    private var isExpanding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityAboutMeBinding.inflate(layoutInflater)
        binding.root.configureAdaptiveMargin(MARGIN_PROVIDER_ADP_DEFAULT, binding.aboutBottomContainer)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root, crossActivityCallbackIsActive)
        applyInsetIfNeeded()
        setupToolbar()

        initContent()
        refreshAppBar(resources.configuration)
        setupOnClickListeners()
        initOnBackPressed()
    }

    private fun applyInsetIfNeeded() {
        if (SDK_INT >= 30 && !window.decorView.fitsSystemWindows) {
            binding.root.setOnApplyWindowInsetsListener { _, insets ->
                val systemBarsInsets = insets.getInsets(systemBars())
                binding.root.setPadding(systemBarsInsets.left, systemBarsInsets.top, systemBarsInsets.right, systemBarsInsets.bottom)
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

    private fun initOnBackPressed() {
        invokeOnBack(
            triggerStateFlow = callbackIsActive,
            onBackPressed = {
                binding.aboutAppBar.setExpanded(true)
                isBackProgressing = false
                isExpanding = false
            },
            onBackStarted = { isBackProgressing = true },
            onBackProgressed = {
                val interpolatedProgress = progressInterpolator.getInterpolation(it.progress)
                if (interpolatedProgress > .5 && !isExpanding) {
                    isExpanding = true
                    binding.aboutAppBar.setExpanded(true, true)
                } else if (interpolatedProgress < .3 && isExpanding) {
                    isExpanding = false
                    binding.aboutAppBar.setExpanded(false, true)
                }
            },
            onBackCancelled = {
                binding.aboutAppBar.setExpanded(false)
                isBackProgressing = false
                isExpanding = false
            }
        )
        updateCallbackState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshAppBar(newConfig)
        updateCallbackState()
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
        val icon = AppCompatResources.getDrawable(this, R.drawable.me4_round)
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
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.commonutils_playstore_ad))
            .setMessage(getString(R.string.commonutils_playstore_redirect_message))
            .setPositiveButton(getString(R.string.commonutils_yes)) { _, _ ->
                openURL(getString(R.string.commonutils_playstore_developer_page_link))
            }
            .setNegativeButton(getString(designR.string.oui_des_common_cancel), null)
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
            aboutBottomRateApp.setOnClickListener { openApp(packageName, false) }
            aboutBottomShareApp.setOnClickListener {
                onShareApp(this@CommonUtilsAboutMeActivity)
                shareApp()
            }
            aboutBottomWriteEmail.setOnClickListener {
                sendEmailAboutMe(getString(R.string.commonutils_email), getString(R.string.commonutils_app_name))
            }
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
            updateCallbackState(appBarLayout.getTotalScrollRange() + verticalOffset == 0)
        }
    }

    private fun updateCallbackState(enable: Boolean? = null) {
        if (isBackProgressing) return
        callbackIsActive.value =
            enable ?: (binding.aboutAppBar.seslIsCollapsed() && isPortrait(resources.configuration) && !isInMultiWindowModeCompat)
        crossActivityCallbackIsActive.value = !callbackIsActive.value
    }

    companion object {
        var onShareApp: (activity: Activity) -> Unit = {}
    }
}