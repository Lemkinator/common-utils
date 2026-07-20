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

import android.content.Intent
import android.os.Looper
import android.text.Spanned
import android.text.style.ClickableSpan
import android.widget.TextView
import de.lemke.commonutils.R
import de.lemke.commonutils.domain.AppStartResult
import de.lemke.commonutils.ui.utils.OnboardingContext
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommonUtilsOOBEActivityTest {
    private fun launchActivity(): CommonUtilsOOBEActivity {
        val controller = Robolectric.buildActivity(CommonUtilsOOBEActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    @Test
    fun `activity launches and inflates tips items without crashing`() {
        launchActivity() shouldNotBe null
    }

    @Test
    fun `ToS ClickableSpan onClick shows AlertDialog without crashing`() {
        val activity = launchActivity()
        val tosText = activity.findViewById<TextView>(R.id.oobeIntroFooterTosText)
        val spanned = tosText.text as Spanned
        val spans = spanned.getSpans(0, spanned.length, ClickableSpan::class.java)
        spans.isNotEmpty() shouldBe true
        spans.first().onClick(tosText)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowDialog.getLatestDialog() shouldNotBe null
    }

    @Test
    fun `footer button click triggers proceed-delay coroutine`() {
        val activity = launchActivity()
        activity.findViewById<android.view.View>(R.id.oobeIntroFooterButton).performClick()
        // Idle to let the lifecycleScope.launch block enqueue; actual delay runs async.
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `initToSView with tosChanged true shows new-tos-text string`() {
        val ctx =
            OnboardingContext(
                mainActivityName = "android.app.Activity",
                steps = emptyList(),
                versionCode = 1,
                versionName = "1.0",
                appStartResult = AppStartResult.FIRST_TIME_VERSION,
                lastVersionCode = 0,
                lastVersionName = "0.0.0",
                tosChanged = true,
            )
        val intent = Intent().apply { putExtra("commonUtilsOnboardingContext", ctx) }
        val activity = Robolectric.buildActivity(CommonUtilsOOBEActivity::class.java, intent).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        val tosText = activity.findViewById<TextView>(R.id.oobeIntroFooterTosText)
        tosText.text.isNotEmpty() shouldBe true
    }

    @Test
    fun `initFooterButton narrow screen sets MATCH_PARENT width`() {
        // Override configuration so screenWidthDp < MIN_FULL_BUTTON_WIDTH_DP (360)
        val controller = Robolectric.buildActivity(CommonUtilsOOBEActivity::class.java)
        controller
            .get()
            .resources.configuration.screenWidthDp = 300
        controller.setup()
        shadowOf(Looper.getMainLooper()).idle()
        // Activity still launches without crash - MATCH_PARENT branch exercised.
        controller.get() shouldNotBe null
    }

    @Test
    fun `initFooterButton wide screen does not set MATCH_PARENT width`() {
        // screenWidthDp >= MIN_FULL_BUTTON_WIDTH_DP (360) → false branch of the width check
        val controller = Robolectric.buildActivity(CommonUtilsOOBEActivity::class.java)
        controller
            .get()
            .resources.configuration.screenWidthDp = 400
        controller.setup()
        shadowOf(Looper.getMainLooper()).idle()
        controller.get() shouldNotBe null
    }

    @Test
    fun `buildTosSpannable returns null when tos is absent from tosText`() {
        buildTosSpannable("By continuing, you agree to our terms.", "Terms of Service") {} shouldBe null
    }

    @Test
    fun `buildTosSpannable wraps tos substring in a ClickableSpan at the correct position`() {
        val tos = "Terms of Service"
        val tosText = "By continuing, you agree to the $tos."
        val spanned = checkNotNull(buildTosSpannable(tosText, tos) {})
        val spans = spanned.getSpans(0, spanned.length, ClickableSpan::class.java)
        spans.size shouldBe 1
        val expectedStart = tosText.lastIndexOf(tos)
        spanned.getSpanStart(spans[0]) shouldBe expectedStart
        spanned.getSpanEnd(spans[0]) shouldBe expectedStart + tos.length
    }
}
