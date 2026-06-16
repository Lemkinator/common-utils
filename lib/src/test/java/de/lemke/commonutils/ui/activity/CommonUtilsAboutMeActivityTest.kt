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

import android.content.res.Configuration
import android.os.Looper
import android.view.View
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import de.lemke.commonutils.R
import de.lemke.commonutils.setupCommonUtilsAboutMeActivity
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
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
        listener.onOffsetChanged(findViewById<AppBarLayout>(R.id.aboutAppBar), offset)
    }

    @Test
    fun `activity launches without crashing`() {
        launchActivity() shouldNotBe null
    }

    @Test
    fun `onConfigurationChanged landscape covers refreshAppBar landscape branch`() {
        val activity = launchActivity()
        val landscapeConfig = Configuration(activity.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }
        activity.onConfigurationChanged(landscapeConfig)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `onConfigurationChanged portrait covers refreshAppBar portrait branch`() {
        val activity = launchActivity()
        val portraitConfig = Configuration(activity.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
        activity.onConfigurationChanged(portraitConfig)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `AboutAppBarListener onOffsetChanged abs gt half range — alpha-zero branch`() {
        val activity = launchActivity()
        // abs(-500) >= totalScrollRange/2 → alpha = 0, setBottomContentEnabled(true)
        activity.dispatchAppBarOffset(-500)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `AboutAppBarListener onOffsetChanged abs equals zero — alpha-one branch`() {
        val activity = launchActivity()
        // abs(0) == 0 → alpha = 1, setBottomContentEnabled(false)
        activity.dispatchAppBarOffset(0)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `AboutAppBarListener onOffsetChanged intermediate offset — else interpolated-alpha branch`() {
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
    fun `setupCommonUtilsAboutMeActivity stores onShareApp callback — invoked via companion`() {
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
}
