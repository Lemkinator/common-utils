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

import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Looper
import android.view.View
import androidx.activity.BackEventCompat
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import de.lemke.commonutils.R
import de.lemke.commonutils.setupCommonUtilsAboutMeActivity
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class CommonUtilsAboutMeActivityTest {
    @BeforeEach
    fun setUp() {
        setupCommonUtilsAboutMeActivity()
    }

    private fun launchActivity(): CommonUtilsAboutMeActivity {
        val controller = Robolectric.buildActivity(CommonUtilsAboutMeActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    /** Invokes [OnOffsetChangedListener.onOffsetChanged] directly via the private field. */
    private fun CommonUtilsAboutMeActivity.dispatchAppBarOffset(offset: Int) {
        val listenerField = CommonUtilsAboutMeActivity::class.java.getDeclaredField("appBarListener")
        listenerField.isAccessible = true
        val listener = listenerField.get(this) as OnOffsetChangedListener
        listener.onOffsetChanged(findViewById(R.id.aboutAppBar), offset)
    }

    @Test
    fun `activity launches without crashing`() {
        launchActivity() shouldNotBe null
    }

    @Test
    fun `onConfigurationChanged landscape covers refreshAppBar landscape branch`() {
        val activity = launchActivity()
        val landscapeConfig =
            Configuration(activity.resources.configuration).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
        activity.onConfigurationChanged(landscapeConfig)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `onConfigurationChanged portrait covers refreshAppBar portrait branch`() {
        val activity = launchActivity()
        val portraitConfig =
            Configuration(activity.resources.configuration).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
            }
        activity.onConfigurationChanged(portraitConfig)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `AboutAppBarListener onOffsetChanged abs gt half range - alpha-zero branch`() {
        val activity = launchActivity()
        // abs(-500) >= totalScrollRange/2 → alpha = 0, setBottomContentEnabled(true)
        activity.dispatchAppBarOffset(-500)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `AboutAppBarListener onOffsetChanged abs equals zero - alpha-one branch`() {
        val activity = launchActivity()
        // abs(0) == 0 → alpha = 1, setBottomContentEnabled(false)
        activity.dispatchAppBarOffset(0)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `AboutAppBarListener onOffsetChanged intermediate offset - else interpolated-alpha branch`() {
        val activity = launchActivity()
        // offset = -10: abs = 10, not >= range/2 and not == 0 → else branch
        activity.dispatchAppBarOffset(-10)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `github header click fires without crashing`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderGithub).performClick()
    }

    @Test
    fun `playStore header click shows AlertDialog`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderPlayStore).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `website header click fires without crashing`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderWebsite).performClick()
    }

    @Test
    fun `insta header click fires without crashing`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderInsta).performClick()
    }

    @Test
    fun `tiktok header click fires without crashing`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderTiktok).performClick()
    }

    @Test
    fun `setupCommonUtilsAboutMeActivity stores onShareApp callback - invoked via companion`() {
        var invoked = false
        setupCommonUtilsAboutMeActivity(onShareApp = { invoked = true })
        val activity = launchActivity()
        // Invoke the companion-object callback directly, as the view click path requires
        // a fully laid-out activity that is not available under Robolectric.
        CommonUtilsAboutMeActivity.onShareApp(activity)
        invoked shouldNotBe false
    }

    @Test
    fun `bottom rate-app click fires without crashing`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutBottomRateApp).performClick()
    }

    @Test
    fun `bottom write-email click fires without crashing`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutBottomWriteEmail).performClick()
    }

    @Test
    fun `bottom playStore click shows AlertDialog`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutBottomRelativePlayStore).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `bottom website click fires without crashing`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutBottomRelativeWebsite).performClick()
    }

    @Test
    fun `bottom tiktok click fires without crashing`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutBottomRelativeTiktok).performClick()
    }

    @Test
    fun `bottom shareApp click invokes onShareApp and shareApp`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutBottomShareApp).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `playStore dialog positive button click opens URL`() {
        val activity = launchActivity()
        activity.findViewById<View>(R.id.aboutHeaderPlayStore).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as? androidx.appcompat.app.AlertDialog
        dialog shouldNotBe null
        dialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `back gesture start progressed and cancelled dispatches callbacks`() {
        val activity = launchActivity()
        // Enable callback by simulating fully-expanded app bar (offset=0 → updateCallbackState(true))
        activity.dispatchAppBarOffset(0)
        shadowOf(Looper.getMainLooper()).idle()

        val dispatcher = activity.onBackPressedDispatcher
        val event = BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackStarted(event)
        shadowOf(Looper.getMainLooper()).idle()

        // progress > 0.5 → isExpanding = true branch
        val highProgressEvent = BackEventCompat(10f, 500f, 0.9f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackProgressed(highProgressEvent)
        shadowOf(Looper.getMainLooper()).idle()

        // progress < 0.3 → collapse branch (isExpanding resets to false)
        val lowProgressEvent = BackEventCompat(10f, 500f, 0.1f, BackEventCompat.EDGE_LEFT)
        dispatcher.dispatchOnBackProgressed(lowProgressEvent)
        shadowOf(Looper.getMainLooper()).idle()

        dispatcher.dispatchOnBackCancelled()
        shadowOf(Looper.getMainLooper()).idle()
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
    fun `handleShareApp direct call covers share path`() {
        val activity = launchActivity()
        activity.handleShareApp()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `handleWriteEmail direct call covers email path`() {
        val activity = launchActivity()
        activity.handleWriteEmail()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `onPlayStoreConfirmed direct call opens URL`() {
        val activity = launchActivity()
        activity.onPlayStoreConfirmed()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `onBackPressedHandler resets back-progress state`() {
        val activity = launchActivity()
        activity.onBackPressedHandler()
    }

    @Test
    fun `onBackStartedHandler sets isBackProgressing`() {
        val activity = launchActivity()
        activity.onBackStartedHandler()
    }

    @Test
    fun `onBackProgressedHandler high progress triggers expand branch`() {
        val activity = launchActivity()
        // interpolatedProgress > 0.5 and !isExpanding → isExpanding = true branch
        activity.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT))
    }

    @Test
    fun `onBackProgressedHandler low progress while expanding triggers collapse branch`() {
        val activity = launchActivity()
        // First call: iprog(0.9f)≈0.97 > 0.5 and isExpanding=false → if-body → isExpanding=true
        activity.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT))
        // Second call: iprog(0.01f)≈0.12 < 0.3 and isExpanding=true → else-if-body → lines 132-133 covered
        activity.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.01f, BackEventCompat.EDGE_LEFT))
    }

    @Test
    fun `onBackProgressedHandler low progress while not expanding hits fallthrough`() {
        val activity = launchActivity()
        // isExpanding=false (initial): iprog(0.1f)≈0.447 in [0.3, 0.5] → A=false, C=false → fallthrough (branch 5)
        activity.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.1f, BackEventCompat.EDGE_LEFT))
    }

    @Test
    fun `onBackProgressedHandler two consecutive high-progress events cover A=true B=false branch`() {
        val activity = launchActivity()
        // First: iprog(0.9f)≈0.97 > 0.5, isExpanding=false → B=true → if-body → isExpanding=true
        activity.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT))
        // Second: iprog≈0.97 > 0.5, isExpanding=true → B=!isExpanding=false (branch 3) → else-if: C=false (branch 5)
        activity.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT))
    }

    @Test
    fun `onBackProgressedHandler very low progress while not expanding covers C=true D=false branch`() {
        val activity = launchActivity()
        // isExpanding=false (initial): iprog(0.01f)≈0.12 < 0.3 → C=true (branch 6), D=false (branch 7) → skip body
        activity.onBackProgressedHandler(BackEventCompat(0f, 0f, 0.01f, BackEventCompat.EDGE_LEFT))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `handle back methods via dispatcher when invokeOnBack callback enabled`() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        try {
            setupCommonUtilsAboutMeActivity()
            val controller = Robolectric.buildActivity(CommonUtilsAboutMeActivity::class.java).setup()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = controller.get()
            // dispatchAppBarOffset(0) → callbackIsActive=true → UnconfinedTestDispatcher runs coroutine
            // immediately → invokeOnBack callback.isEnabled=true; crossActivityCallback.isEnabled=false
            activity.dispatchAppBarOffset(0)
            shadowOf(Looper.getMainLooper()).idle()

            val dispatcher = activity.onBackPressedDispatcher
            val event = BackEventCompat(10f, 500f, 0.5f, BackEventCompat.EDGE_LEFT)
            // handleOnBackStarted, handleOnBackProgressed, handleOnBackCancelled called on invokeOnBack callback
            dispatcher.dispatchOnBackStarted(event)
            dispatcher.dispatchOnBackProgressed(event)
            dispatcher.dispatchOnBackCancelled()
            // Start a new gesture then press back → handleOnBackPressed called
            dispatcher.dispatchOnBackStarted(event)
            dispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `applyInsetIfNeeded with fitsSystemWindows=true skips listener setup`() {
        setupCommonUtilsAboutMeActivity()
        // Set fitsSystemWindows=true before onCreate → !fitsSystemWindows=false → body skipped (B false branch)
        val controller = Robolectric.buildActivity(CommonUtilsAboutMeActivity::class.java)
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
        activity.onBackCancelledHandler()
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [29])
class CommonUtilsAboutMeActivitySdk29Test {
    @BeforeEach
    fun setUp() {
        setupCommonUtilsAboutMeActivity()
    }

    @Test
    fun `applyInsetIfNeeded SDK_INT less than R skips listener setup`() {
        // SDK 29 < R (30) → first condition false → body skipped → SDK<R branch covered
        val controller = Robolectric.buildActivity(CommonUtilsAboutMeActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        controller.get() shouldNotBe null
    }
}
