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
import android.os.Looper
import android.view.View
import androidx.activity.BackEventCompat
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
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

    /** Reads the private `isExpanding` field via reflection - the controller exposes no public getter for it. */
    private val CommonUtilsAboutMeActivity.PredictiveBackGestureController.isExpanding: Boolean
        get() {
            val field = javaClass.getDeclaredField("isExpanding")
            field.isAccessible = true
            return field.getBoolean(this)
        }

    /** Invokes [OnOffsetChangedListener.onOffsetChanged] directly via the private field. */
    private fun CommonUtilsAboutMeActivity.dispatchAppBarOffset(offset: Int) {
        val listenerField = CommonUtilsAboutMeActivity::class.java.getDeclaredField("appBarListener")
        listenerField.isAccessible = true
        val listener = listenerField.get(this) as OnOffsetChangedListener
        listener.onOffsetChanged(findViewById(R.id.aboutAppBar), offset)
    }

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
        dispatchAppBarOffset(-findViewById<AppBarLayout>(R.id.aboutAppBar).totalScrollRange)
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
        activity.dispatchAppBarOffset(-600)
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<View>(R.id.aboutSwipeUpContainer).alpha shouldBe 0f
        activity.bottomContentEnabled.shouldBeTrue()
    }

    @Test
    fun `AboutAppBarListener onOffsetChanged abs equals zero shows swipe-up and disables bottom content`() {
        val activity = launchActivity()
        activity.dispatchAppBarOffset(-1000)
        activity.dispatchAppBarOffset(0)
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<View>(R.id.aboutSwipeUpContainer).alpha shouldBe 1f
        activity.bottomContentEnabled.shouldBeFalse()
    }

    @Test
    fun `AboutAppBarListener onOffsetChanged intermediate offset fades swipe-up with the app bar position`() {
        val activity = launchActivity()
        val appBar = activity.findViewById<AppBarLayout>(R.id.aboutAppBar)
        appBar.y = -appBar.totalScrollRange / 10f
        activity.dispatchAppBarOffset(-10)
        shadowOf(Looper.getMainLooper()).idle()
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
    fun `back gesture start progressed and cancelled dispatches callbacks`() {
        val activity = launchActivity()
        activity.collapseAppBar()
        shadowOf(Looper.getMainLooper()).idle()

        val dispatcher = activity.onBackPressedDispatcher
        val event = BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackStarted(event)
        shadowOf(Looper.getMainLooper()).idle()
        activity.backGesture.isBackProgressing.shouldBeTrue()

        val highProgressEvent = BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackProgressed(highProgressEvent)
        shadowOf(Looper.getMainLooper()).idle()
        activity.backGesture.isExpanding.shouldBeTrue()

        val lowProgressEvent = BackEventCompat(10f, 500f, 0.01f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackProgressed(lowProgressEvent)
        shadowOf(Looper.getMainLooper()).idle()
        activity.backGesture.isExpanding.shouldBeFalse()

        dispatcher.dispatchOnBackCancelled()
        shadowOf(Looper.getMainLooper()).idle()
        activity.backGesture.isBackProgressing.shouldBeFalse()
    }

    @Test
    fun `back gesture pressed finishes activity`() {
        val activity = launchActivity()
        activity.dispatchAppBarOffset(0)
        shadowOf(Looper.getMainLooper()).idle()

        activity.onBackPressedDispatcher.onBackPressed()
        activity.isFinishing.shouldBeTrue()
    }

    @Test
    fun `predictive back callback isEnabled follows the collapsed-portrait condition`() {
        val activity = launchActivity()
        val totalScrollRange = activity.findViewById<AppBarLayout>(R.id.aboutAppBar).totalScrollRange

        activity.dispatchAppBarOffset(-totalScrollRange)
        shadowOf(Looper.getMainLooper()).idle()
        activity.backGesture.callback.isEnabled
            .shouldBeTrue()

        activity.dispatchAppBarOffset(-totalScrollRange + 1)
        shadowOf(Looper.getMainLooper()).idle()
        activity.backGesture.callback.isEnabled
            .shouldBeFalse()
    }

    @Test
    fun `handleShareApp invokes onShareApp and shares the Play Store link`() {
        var sharedFrom: Activity? = null
        setupCommonUtilsAboutMeActivity(onShareApp = { sharedFrom = it })
        val activity = launchActivity()
        activity.handleShareApp()
        sharedFrom shouldBeSameInstanceAs activity
        activity.shouldHaveSharedApp()
    }

    @Test
    fun `handleWriteEmail composes the contact email`() {
        val activity = launchActivity()
        activity.handleWriteEmail()
        activity.shouldHaveStartedAboutMeEmail()
    }

    @Test
    fun `onPlayStoreConfirmed opens the developer page`() {
        val activity = launchActivity()
        activity.onPlayStoreConfirmed()
        activity.shouldHaveOpenedUrl("https://play.google.com/store/apps/dev?id=4961154860499174073")
    }

    @Test
    fun `onBackPressedHandler resets back-progress state`() {
        val activity = launchActivity()
        activity.backGesture.onBackStartedHandler()
        activity.backGesture.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT))

        activity.backGesture.onBackPressedHandler()

        activity.backGesture.isBackProgressing.shouldBeFalse()
        activity.backGesture.isExpanding.shouldBeFalse()
    }

    @Test
    fun `onBackStartedHandler sets isBackProgressing`() {
        val activity = launchActivity()
        activity.backGesture.onBackStartedHandler()
        activity.backGesture.isBackProgressing.shouldBeTrue()
    }

    @Test
    fun `onBackProgressedHandler high progress triggers expand branch`() {
        val activity = launchActivity()
        activity.backGesture.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT))
        activity.backGesture.isExpanding.shouldBeTrue()
    }

    @Test
    fun `onBackProgressedHandler low progress while expanding triggers collapse branch`() {
        val activity = launchActivity()
        activity.backGesture.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT))
        activity.backGesture.isExpanding.shouldBeTrue()
        activity.backGesture.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.01f, BackEventCompat.EDGE_LEFT))
        activity.backGesture.isExpanding.shouldBeFalse()
    }

    @Test
    fun `onBackProgressedHandler low progress while not expanding hits fallthrough`() {
        val activity = launchActivity()
        activity.backGesture.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.1f, BackEventCompat.EDGE_LEFT))
        activity.backGesture.isExpanding.shouldBeFalse()
    }

    @Test
    fun `onBackProgressedHandler two consecutive high-progress events cover A=true B=false branch`() {
        val activity = launchActivity()
        activity.backGesture.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT))
        activity.backGesture.isExpanding.shouldBeTrue()
        activity.backGesture.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT))
        activity.backGesture.isExpanding.shouldBeTrue()
    }

    @Test
    fun `onBackProgressedHandler very low progress while not expanding covers C=true D=false branch`() {
        val activity = launchActivity()
        activity.backGesture.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.01f, BackEventCompat.EDGE_LEFT))
        activity.backGesture.isExpanding.shouldBeFalse()
    }

    @Test
    fun `back gesture callback overrides delegate to the handler methods`() {
        val activity = launchActivity()
        val callback = activity.backGesture.callback
        val startedEvent = BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT)
        val expandingEvent = BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT)

        callback.handleOnBackStarted(startedEvent)
        activity.backGesture.isBackProgressing.shouldBeTrue()

        callback.handleOnBackProgressed(expandingEvent)
        activity.backGesture.isExpanding.shouldBeTrue()

        callback.handleOnBackCancelled()
        activity.backGesture.isBackProgressing.shouldBeFalse()
        activity.backGesture.isExpanding.shouldBeFalse()

        callback.handleOnBackStarted(startedEvent)
        callback.handleOnBackPressed()
        activity.backGesture.isBackProgressing.shouldBeFalse()
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
        controller.get() shouldNotBe null
    }

    @Test
    fun `onBackCancelledHandler resets back-progress state`() {
        val activity = launchActivity()
        activity.backGesture.onBackStartedHandler()
        activity.backGesture.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT))

        activity.backGesture.onBackCancelledHandler()

        activity.backGesture.isBackProgressing.shouldBeFalse()
        activity.backGesture.isExpanding.shouldBeFalse()
    }
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
        controller.get() shouldNotBe null
    }
}
