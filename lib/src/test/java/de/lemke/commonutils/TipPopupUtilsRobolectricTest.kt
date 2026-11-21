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
package de.lemke.commonutils

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import de.lemke.commonutils.ui.utils.showDimmingTipPopup
import de.lemke.commonutils.ui.utils.showTouchBlockingTipPopup
import de.lemke.commonutils.ui.widget.DimmingView
import de.lemke.commonutils.ui.widget.TouchBlockingView
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TipPopupUtilsRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private val activity =
        Robolectric
            .buildActivity(NoActionBarTestActivity::class.java)
            .setup()
            .track(destroyActivities)
            .get()
    private val anchor = View(activity).also { activity.setContentView(it) }
    private val root get() = activity.window.decorView.rootView as ViewGroup

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun popupActionButton(text: String): View =
        Shadow
            .extract<ShadowWindowManagerImpl>(activity.windowManager)
            .views
            .flatMap { it.allViews() }
            .filterIsInstance<TextView>()
            .single { it.text.toString() == text }

    private fun View.allViews(): List<View> = listOf(this) + (this as? ViewGroup)?.children.orEmpty().flatMap { it.allViews() }

    @Test
    fun `showDimmingTipPopup adds a dimming overlay and removes it with the action`() {
        var acted = false

        anchor.showDimmingTipPopup("Tip message", "Got it") { acted = true }
        idle()
        root.children.any { it is DimmingView }.shouldBeTrue()

        popupActionButton("Got it").performClick()
        idle()

        acted.shouldBeTrue()
        root.children.any { it is DimmingView }.shouldBeFalse()
    }

    @Test
    fun `showDimmingTipPopup labels the action with the OK text by default`() {
        anchor.showDimmingTipPopup("Tip message")
        idle()

        popupActionButton(activity.getString(R.string.commonutils_ok)).isShown shouldBe true
    }

    @Test
    fun `showTouchBlockingTipPopup adds a touch blocking overlay and removes it with the action`() {
        var acted = false

        anchor.showTouchBlockingTipPopup("Tip message", "Got it") { acted = true }
        idle()
        root.children.any { it is TouchBlockingView }.shouldBeTrue()

        popupActionButton("Got it").performClick()
        idle()

        acted.shouldBeTrue()
        root.children.any { it is TouchBlockingView }.shouldBeFalse()
    }

    @Test
    fun `showDimmingTipPopup resolves message and action from string resources`() {
        anchor.showDimmingTipPopup(R.string.commonutils_share, R.string.commonutils_delete)
        idle()

        popupActionButton("Share").isShown shouldBe true
        popupActionButton("Delete").isShown shouldBe true
        root.children.count { it.javaClass == DimmingView::class.java } shouldBe 1
    }

    @Test
    fun `showTouchBlockingTipPopup resolves message and action from string resources`() {
        anchor.showTouchBlockingTipPopup(R.string.commonutils_share, R.string.commonutils_delete)
        idle()

        popupActionButton("Share").isShown shouldBe true
        popupActionButton("Delete").isShown shouldBe true
        root.children.count { it is TouchBlockingView } shouldBe 1
    }
}
