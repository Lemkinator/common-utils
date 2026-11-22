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

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Insets
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.activity.BackEventCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.IntentCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.appbar.AppBarLayout
import de.lemke.commonutils.DestroyActivitiesRule
import de.lemke.commonutils.R
import de.lemke.commonutils.track
import de.lemke.commonutils.ui.utils.setupCommonUtilsAboutMeActivity
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.time.Duration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import androidx.core.graphics.Insets as CompatInsets
import dev.oneuiproject.oneui.design.R as designR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommonUtilsAboutMeActivityTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    @Before
    fun setUp() {
        setupCommonUtilsAboutMeActivity()
    }

    private fun launchActivity(): CommonUtilsAboutMeActivity {
        val controller = Robolectric.buildActivity(CommonUtilsAboutMeActivity::class.java).setup().track(destroyActivities)
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    private fun CommonUtilsAboutMeActivity.appBarBehavior(): AppBarLayout.Behavior =
        (findViewById<AppBarLayout>(R.id.aboutAppBar).layoutParams as CoordinatorLayout.LayoutParams).behavior as AppBarLayout.Behavior

    private val CommonUtilsAboutMeActivity.appBarOffset: Int
        get() = appBarBehavior().topAndBottomOffset

    private val CommonUtilsAboutMeActivity.totalScrollRange: Int
        get() = findViewById<AppBarLayout>(R.id.aboutAppBar).totalScrollRange

    /** Moves the app bar through its own behavior and lays it out, so the offset listener runs as it does on a device. */
    private fun CommonUtilsAboutMeActivity.setAppBarOffset(offset: Int) {
        appBarBehavior().setTopAndBottomOffset(offset)
        findViewById<AppBarLayout>(R.id.aboutAppBar).requestLayout()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun idleAnimations() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    private fun CommonUtilsAboutMeActivity.portraitConfig() =
        Configuration(resources.configuration).apply { orientation = Configuration.ORIENTATION_PORTRAIT }

    private fun CommonUtilsAboutMeActivity.startedIntent(): Intent = shadowOf(this).nextStartedActivity.shouldNotBeNull()

    private fun CommonUtilsAboutMeActivity.shouldHaveOpenedUrl(url: String) {
        val intent = startedIntent()
        intent.action shouldBe Intent.ACTION_VIEW
        intent.dataString shouldBe url
    }

    private fun CommonUtilsAboutMeActivity.shouldHaveSharedApp() {
        val chooser = startedIntent()
        chooser.action shouldBe Intent.ACTION_CHOOSER
        val target = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java).shouldNotBeNull()
        target.action shouldBe Intent.ACTION_SEND
        target.type shouldBe "text/plain"
        target.getStringExtra(Intent.EXTRA_TEXT) shouldBe "https://play.google.com/store/apps/details?id=de.lemke.commonutils.test"
    }

    private fun CommonUtilsAboutMeActivity.shouldHaveStartedAboutMeEmail() {
        val intent = startedIntent()
        intent.action shouldBe Intent.ACTION_SENDTO
        intent.dataString shouldBe "mailto:"
        intent.getStringArrayExtra(Intent.EXTRA_EMAIL) shouldBe arrayOf("apps@leonard-lemke.com")
        intent.getStringExtra(Intent.EXTRA_SUBJECT) shouldBe "CommonUtils"
        intent.getStringExtra(Intent.EXTRA_TEXT) shouldBe "This is the best app I’ve ever seen in my life!"
    }

    /** `CardItemView` registers its click listener on the inner container, so a click on the card itself reaches no listener. */
    private fun View.performCardClick() {
        findViewById<View>(designR.id.cardview_container).performClick()
    }

    private fun CommonUtilsAboutMeActivity.collapseAppBar() {
        findViewById<AppBarLayout>(R.id.aboutAppBar).setExpanded(false, false)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val CommonUtilsAboutMeActivity.bottomContentEnabled: Boolean
        get() = findViewById<View>(R.id.aboutBottomContent).isEnabled

    @Test
    fun `activity launches without crashing`() {
        launchActivity() shouldNotBe null
    }

    @Test
    fun `onConfigurationChanged landscape hides the swipe-up container and shows the bottom content`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutBottomContainer).alpha = 0.2f
        val landscapeConfig =
            Configuration(activity.resources.configuration).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
        activity.onConfigurationChanged(landscapeConfig)
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<View>(R.id.aboutSwipeUpContainer).isVisible.shouldBeFalse()
        activity.findViewById<View>(R.id.aboutBottomContainer).alpha shouldBe 1f
        activity.bottomContentEnabled.shouldBeTrue()
    }

    @Test
    fun `onConfigurationChanged portrait shows the swipe-up container at half the screen height`() {
        val activity = launchActivity()
        val swipeUp = activity.findViewById<View>(R.id.aboutSwipeUpContainer)
        swipeUp.isVisible = false
        val portraitConfig =
            Configuration(activity.resources.configuration).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
            }
        activity.onConfigurationChanged(portraitConfig)
        shadowOf(Looper.getMainLooper()).idle()
        swipeUp.isVisible.shouldBeTrue()
        swipeUp.layoutParams.height shouldBe activity.resources.displayMetrics.heightPixels / 2
    }

    @Test
    fun `AboutAppBarListener onOffsetChanged abs gt half range hides swipe-up and enables bottom content`() {
        val activity = launchActivity()
        activity.setAppBarOffset(-activity.findViewById<AppBarLayout>(R.id.aboutAppBar).totalScrollRange)
        activity.findViewById<View>(R.id.aboutSwipeUpContainer).alpha shouldBe 0f
        activity.bottomContentEnabled.shouldBeTrue()
    }

    @Test
    fun `AboutAppBarListener onOffsetChanged abs equals zero shows swipe-up and disables bottom content`() {
        val activity = launchActivity()
        activity.setAppBarOffset(-activity.findViewById<AppBarLayout>(R.id.aboutAppBar).totalScrollRange)
        activity.setAppBarOffset(0)
        activity.findViewById<View>(R.id.aboutSwipeUpContainer).alpha shouldBe 1f
        activity.bottomContentEnabled.shouldBeFalse()
    }

    @Test
    fun `AboutAppBarListener onOffsetChanged intermediate offset fades swipe-up with the app bar position`() {
        val activity = launchActivity()
        val appBar = activity.findViewById<AppBarLayout>(R.id.aboutAppBar)
        activity.setAppBarOffset(-appBar.totalScrollRange / 10)
        activity.findViewById<View>(R.id.aboutSwipeUpContainer).alpha shouldBe (0.7f plusOrMinus 0.001f)
    }

    @Test
    fun `github header click opens the GitHub profile`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderGithub).performClick()
        activity.shouldHaveOpenedUrl("https://github.com/Lemkinator")
    }

    @Test
    fun `playStore header click shows AlertDialog`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderPlayStore).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        ShadowDialog.getLatestDialog() shouldNotBe null
    }

    @Test
    fun `website header click opens the website`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderWebsite).performClick()
        activity.shouldHaveOpenedUrl("https://www.leonard-lemke.com")
    }

    @Test
    fun `insta header click opens the Instagram profile`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderInsta).performClick()
        activity.shouldHaveOpenedUrl("https://www.instagram.com/lemkinator5/")
    }

    @Test
    fun `tiktok header click opens the redirect link`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderTiktok).performClick()
        activity.shouldHaveOpenedUrl("https://www.leonard-lemke.com/rr")
    }

    @Test
    fun `setupCommonUtilsAboutMeActivity stores onShareApp callback - invoked via companion`() {
        var invoked = false
        setupCommonUtilsAboutMeActivity(onShareApp = { invoked = true })
        val activity = launchActivity()
        CommonUtilsAboutMeActivity.onShareApp(activity)
        invoked.shouldBeTrue()
    }

    @Test
    fun `bottom rate-app click opens the app page in the store`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        activity.findViewById<View>(R.id.aboutBottomRateApp).performCardClick()
        activity.shouldHaveOpenedUrl("market://details?id=de.lemke.commonutils.test")
    }

    @Test
    fun `bottom write-email click composes the contact email`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        activity.findViewById<View>(R.id.aboutBottomWriteEmail).performCardClick()
        activity.shouldHaveStartedAboutMeEmail()
    }

    @Test
    fun `bottom playStore click shows AlertDialog`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutBottomRelativePlayStore).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        ShadowDialog.getLatestDialog() shouldNotBe null
    }

    @Test
    fun `bottom website click opens the website`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        activity.findViewById<View>(R.id.aboutBottomRelativeWebsite).performClick()
        activity.shouldHaveOpenedUrl("https://www.leonard-lemke.com")
    }

    @Test
    fun `bottom tiktok click opens the redirect link`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        activity.findViewById<View>(R.id.aboutBottomRelativeTiktok).performClick()
        activity.shouldHaveOpenedUrl("https://www.leonard-lemke.com/rr")
    }

    @Test
    fun `bottom shareApp click invokes onShareApp and shares the Play Store link`() {
        var sharedFrom: Activity? = null
        setupCommonUtilsAboutMeActivity(onShareApp = { sharedFrom = it })
        val activity = launchActivity()
        activity.collapseAppBar()
        activity.findViewById<View>(R.id.aboutBottomShareApp).performCardClick()
        shadowOf(Looper.getMainLooper()).idle()
        sharedFrom shouldBeSameInstanceAs activity
        activity.shouldHaveSharedApp()
    }

    @Test
    fun `playStore dialog positive button click opens URL`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderPlayStore).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as? androidx.appcompat.app.AlertDialog
        dialog.shouldNotBeNull().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.shouldHaveOpenedUrl("https://play.google.com/store/apps/dev?id=4961154860499174073")
    }

    @Test
    fun `back gesture progress expands and collapses the app bar and cancel collapses it`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        val dispatcher = activity.onBackPressedDispatcher

        dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()
        activity.appBarOffset shouldBe 0

        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.01f, BackEventCompat.EDGE_LEFT))
        idleAnimations()
        activity.appBarOffset shouldBe -activity.totalScrollRange

        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()
        activity.appBarOffset shouldBe 0
        dispatcher.dispatchOnBackCancelled()
        idleAnimations()
        activity.appBarOffset shouldBe -activity.totalScrollRange
    }

    @Test
    fun `back gesture progress between the thresholds keeps an expanding app bar expanded`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        val dispatcher = activity.onBackPressedDispatcher

        dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()
        activity.appBarOffset shouldBe 0
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.1f, BackEventCompat.EDGE_LEFT))
        idleAnimations()

        activity.appBarOffset shouldBe 0
    }

    @Test
    fun `back gesture progress below the collapse threshold collapses an expanding app bar`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        val dispatcher = activity.onBackPressedDispatcher

        dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()
        activity.appBarOffset shouldBe 0
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.01f, BackEventCompat.EDGE_LEFT))
        idleAnimations()

        activity.appBarOffset shouldBe -activity.totalScrollRange
    }

    @Test
    fun `back gesture progress below the collapse threshold before expanding leaves the app bar collapsed until progress rises`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        val dispatcher = activity.onBackPressedDispatcher

        dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.01f, BackEventCompat.EDGE_LEFT))
        idleAnimations()
        activity.appBarOffset shouldBe -activity.totalScrollRange
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()

        activity.appBarOffset shouldBe 0
    }

    @Test
    fun `second high progress event while expanding does not expand the app bar again`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        val dispatcher = activity.onBackPressedDispatcher

        dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()
        activity.appBarOffset shouldBe 0
        activity.collapseAppBar()
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.95f, BackEventCompat.EDGE_LEFT))
        idleAnimations()

        activity.appBarOffset shouldBe -activity.totalScrollRange
    }

    @Test
    fun `cancelled back gesture hands the next back press to the activity once the bar is expanded`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        val dispatcher = activity.onBackPressedDispatcher

        dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()
        dispatcher.dispatchOnBackCancelled()
        idleAnimations()
        activity.setAppBarOffset(0)
        dispatcher.onBackPressed()

        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `new back gesture after a cancel expands the app bar again`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        val dispatcher = activity.onBackPressedDispatcher
        dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()
        dispatcher.dispatchOnBackCancelled()
        idleAnimations()
        activity.appBarOffset shouldBe -activity.totalScrollRange

        dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()

        activity.appBarOffset shouldBe 0
    }

    @Test
    fun `new back gesture after a confirmed back press expands the app bar again`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        val dispatcher = activity.onBackPressedDispatcher
        dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()
        dispatcher.onBackPressed()
        idleAnimations()
        activity.appBarOffset shouldBe 0
        activity.collapseAppBar()
        idleAnimations()

        dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()

        activity.appBarOffset shouldBe 0
    }

    @Test
    fun `back press while collapsed expands the app bar instead of finishing`() {
        val activity = launchActivity()
        activity.collapseAppBar()

        activity.onBackPressedDispatcher.onBackPressed()
        idleAnimations()

        activity.isFinishing.shouldBeFalse()
        activity.appBarOffset shouldBe 0
    }

    @Test
    fun `back press while expanded finishes the activity`() {
        val activity = launchActivity()
        activity.setAppBarOffset(0)

        activity.onBackPressedDispatcher.onBackPressed()

        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `back press one pixel short of collapsed finishes the activity`() {
        val activity = launchActivity()

        activity.setAppBarOffset(-activity.totalScrollRange + 1)
        activity.onBackPressedDispatcher.onBackPressed()

        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `back press stays handled by the app bar while a back gesture progresses`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        val dispatcher = activity.onBackPressedDispatcher

        dispatcher.dispatchOnBackStarted(BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT))
        idleAnimations()
        dispatcher.onBackPressed()

        activity.isFinishing.shouldBeFalse()
    }

    @Test
    fun `portrait configuration change expands a collapsed app bar and shows the swipe-up container`() {
        val activity = launchActivity()
        activity.collapseAppBar()

        activity.onConfigurationChanged(activity.portraitConfig())
        shadowOf(Looper.getMainLooper()).idle()

        activity.appBarOffset shouldBe 0
        activity.findViewById<View>(R.id.aboutSwipeUpContainer).isVisible.shouldBeTrue()
    }

    @Test
    @Config(sdk = [36], qualifiers = "land")
    fun `collapsed landscape app bar leaves back to the activity`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        activity.onConfigurationChanged(activity.resources.configuration)
        shadowOf(Looper.getMainLooper()).idle()

        activity.onBackPressedDispatcher.onBackPressed()

        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `multi-window mode hides the swipe-up container and leaves back to the activity`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        shadowOf(activity).setInMultiWindowMode(true)
        activity.onConfigurationChanged(activity.portraitConfig())
        shadowOf(Looper.getMainLooper()).idle()

        activity.findViewById<View>(R.id.aboutSwipeUpContainer).isVisible.shouldBeFalse()
        activity.onBackPressedDispatcher.onBackPressed()
        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `applyInsetIfNeeded with fitsSystemWindows=true skips listener setup`() {
        setupCommonUtilsAboutMeActivity()
        val controller = Robolectric.buildActivity(CommonUtilsAboutMeActivity::class.java).track(destroyActivities)
        controller
            .get()
            .window.decorView.fitsSystemWindows = true
        controller.setup()
        shadowOf(Looper.getMainLooper()).idle()
        controller.get().paddingAfter(systemBarInsets()) shouldBe listOf(0, 0, 0, 0)
    }
}

private fun systemBarInsets(): WindowInsets =
    WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(), Insets.of(11, 22, 33, 44)).build()

private fun legacySystemWindowInsets(): WindowInsets =
    WindowInsetsCompat
        .Builder()
        .setInsets(WindowInsetsCompat.Type.systemBars(), CompatInsets.of(11, 22, 33, 44))
        .build()
        .toWindowInsets()
        .shouldNotBeNull()

private fun Activity.paddingAfter(insets: WindowInsets): List<Int> {
    val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
    root.dispatchApplyWindowInsets(insets)
    return listOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CommonUtilsAboutMeActivitySdk29Test {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    @Before
    fun setUp() {
        setupCommonUtilsAboutMeActivity()
    }

    @Test
    fun `applyInsetIfNeeded SDK_INT less than R skips listener setup`() {
        val controller = Robolectric.buildActivity(CommonUtilsAboutMeActivity::class.java).setup().track(destroyActivities)
        shadowOf(Looper.getMainLooper()).idle()
        controller.get().paddingAfter(legacySystemWindowInsets()) shouldBe listOf(0, 0, 0, 0)
    }
}
