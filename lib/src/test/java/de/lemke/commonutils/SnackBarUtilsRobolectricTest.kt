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
import androidx.appcompat.app.AppCompatActivity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import com.google.android.material.R as MaterialR

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class SnackBarUtilsActivityRobolectricTest {
    private fun setupActivity(): AppCompatActivity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()

    @Test
    fun `Activity suggestiveSnackBar String shows snackbar`() {
        val snackbar = setupActivity().suggestiveSnackBar("Test message")
        snackbar shouldNotBe null
    }

    @Test
    fun `Activity suggestiveSnackBar String with actionText covers non-null let branch`() {
        val snackbar = setupActivity().suggestiveSnackBar("Test message", actionText = "Undo")
        snackbar shouldNotBe null
    }

    @Test
    fun `Activity suggestiveSnackBar String with explicit view and duration`() {
        val activity = setupActivity()
        val snackbar = activity.suggestiveSnackBar("Test message", view = activity.window.decorView, duration = 5000)
        snackbar shouldNotBe null
    }

    @Test
    fun `Activity suggestiveSnackBar StringRes delegates to String overload`() {
        val snackbar = setupActivity().suggestiveSnackBar(android.R.string.ok)
        snackbar shouldNotBe null
    }

    @Test
    fun `Activity suggestiveSnackBar StringRes with actionText covers let branch`() {
        val snackbar = setupActivity().suggestiveSnackBar(android.R.string.ok, actionText = "Dismiss")
        snackbar shouldNotBe null
    }

    @Test
    fun `Activity suggestiveSnackBar String action button click invokes custom action`() {
        val activity = setupActivity()
        var called = false
        val snackbar = activity.suggestiveSnackBar("msg", actionText = "Act", action = { called = true })
        shadowOf(Looper.getMainLooper()).idle()
        snackbar.view.findViewById<View>(MaterialR.id.snackbar_action)?.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        called shouldBe true
    }

    @Test
    fun `Activity suggestiveSnackBar String action button click invokes default dismiss`() {
        val activity = setupActivity()
        val snackbar = activity.suggestiveSnackBar("msg", actionText = "Dismiss")
        shadowOf(Looper.getMainLooper()).idle()
        snackbar.view.findViewById<View>(MaterialR.id.snackbar_action)?.performClick()
        // runToEndOfTasks advances past the Snackbar dismiss animation delay so isShown reflects
        // the post-animation state (view removed from hierarchy).
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        snackbar.isShown shouldBe false
    }

    @Test
    fun `Activity suggestiveSnackBar StringRes action button click invokes default dismiss`() {
        val activity = setupActivity()
        val snackbar = activity.suggestiveSnackBar(android.R.string.ok, actionText = "Dismiss")
        shadowOf(Looper.getMainLooper()).idle()
        snackbar.view.findViewById<View>(MaterialR.id.snackbar_action)?.performClick()
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        snackbar.isShown shouldBe false
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class SnackBarUtilsFragmentRobolectricTest {
    private fun setupFragment(): ViewFragment {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val fragment = ViewFragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()
        return fragment
    }

    @Test
    fun `Fragment suggestiveSnackBar String shows snackbar via requireActivity`() {
        val snackbar = setupFragment().suggestiveSnackBar("Test message")
        snackbar shouldNotBe null
    }

    @Test
    fun `Fragment suggestiveSnackBar String with actionText covers non-null let branch`() {
        val snackbar = setupFragment().suggestiveSnackBar("Test message", actionText = "Undo")
        snackbar shouldNotBe null
    }

    @Test
    fun `Fragment suggestiveSnackBar StringRes delegates to String overload`() {
        val snackbar = setupFragment().suggestiveSnackBar(android.R.string.ok)
        snackbar shouldNotBe null
    }

    @Test
    fun `Fragment suggestiveSnackBar StringRes with actionText covers let branch`() {
        val snackbar = setupFragment().suggestiveSnackBar(android.R.string.ok, actionText = "Dismiss")
        snackbar shouldNotBe null
    }

    @Test
    fun `Fragment suggestiveSnackBar String action button click invokes default dismiss`() {
        val fragment = setupFragment()
        val snackbar = fragment.suggestiveSnackBar("msg", actionText = "Dismiss")
        shadowOf(Looper.getMainLooper()).idle()
        snackbar.view.findViewById<View>(MaterialR.id.snackbar_action)?.performClick()
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        snackbar.isShown shouldBe false
    }

    @Test
    fun `Fragment suggestiveSnackBar StringRes action button click invokes default dismiss`() {
        val fragment = setupFragment()
        val snackbar = fragment.suggestiveSnackBar(android.R.string.ok, actionText = "Dismiss")
        shadowOf(Looper.getMainLooper()).idle()
        snackbar.view.findViewById<View>(MaterialR.id.snackbar_action)?.performClick()
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        snackbar.isShown shouldBe false
    }
}
