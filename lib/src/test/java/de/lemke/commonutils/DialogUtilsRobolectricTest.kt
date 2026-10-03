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

import android.app.Dialog
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import de.lemke.commonutils.ui.utils.showOnce
import de.lemke.commonutils.ui.utils.showTosDialogOnce
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.lang.ref.WeakReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

private const val TAG = "dialog"
private const val GC_ATTEMPTS = 50

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DialogUtilsRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    @get:Rule
    val drainMainLooper = DrainMainLooperRule()

    private val mainLooper get() = shadowOf(Looper.getMainLooper())

    // ── DialogFragment ─────────────────────────────────────────────────────────

    @Test
    fun `dialog fragment with a free tag shows`() {
        val manager = resumed().get().supportFragmentManager
        val dialog = TestDialogFragment()

        dialog.showOnce(manager, TAG).shouldBeTrue()
        mainLooper.idle()

        manager.findFragmentByTag(TAG) shouldBe dialog
        dialog.dialog?.isShowing shouldBe true
    }

    @Test
    fun `dialog fragment with a pending tag skips`() {
        val manager = resumed().get().supportFragmentManager

        TestDialogFragment().showOnce(manager, TAG).shouldBeTrue()
        TestDialogFragment().showOnce(manager, TAG).shouldBeFalse()
        mainLooper.idle()

        manager.dialogFragments(TAG) shouldBe 1
    }

    @Test
    fun `dialog fragment with a showing tag skips`() {
        val manager = resumed().get().supportFragmentManager
        TestDialogFragment().showOnce(manager, TAG)
        mainLooper.idle()

        TestDialogFragment().showOnce(manager, TAG).shouldBeFalse()
        mainLooper.idle()

        manager.dialogFragments(TAG) shouldBe 1
    }

    @Test
    fun `dialog fragment with a dismiss-pending tag shows`() {
        val manager = resumed().get().supportFragmentManager
        val first = TestDialogFragment()
        first.showOnce(manager, TAG)
        mainLooper.idle()
        first.dismiss()
        val second = TestDialogFragment()

        second.showOnce(manager, TAG).shouldBeTrue()
        mainLooper.idle()

        manager.findFragmentByTag(TAG) shouldBe second
    }

    @Test
    fun `dialog fragment with a removing tag shows`() {
        val manager = resumed().get().supportFragmentManager
        val first = TestDialogFragment()
        first.showOnce(manager, TAG)
        mainLooper.idle()
        val second = TestDialogFragment()
        var shownWhileRemoving: Boolean? = null
        first.onDestroyAction = { shownWhileRemoving = second.showOnce(manager, TAG) }

        manager.beginTransaction().remove(first).commitNow()
        mainLooper.idle()

        shownWhileRemoving shouldBe true
        manager.findFragmentByTag(TAG) shouldBe second
    }

    @Test
    fun `dialog fragment restored after rotation skips before it starts`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        TestDialogFragment().showOnce(controller.get().supportFragmentManager, TAG)
        mainLooper.idle()
        val state = Bundle()
        controller
            .saveInstanceState(state)
            .pause()
            .stop()
            .destroy()
        val restored = Robolectric.buildActivity(AppCompatActivity::class.java).create(state).track(destroyActivities)
        val manager = restored.get().supportFragmentManager

        TestDialogFragment().showOnce(manager, TAG).shouldBeFalse()
        restored.start().resume()
        mainLooper.idle()

        manager.dialogFragments(TAG) shouldBe 1
        (manager.findFragmentByTag(TAG) as DialogFragment).dialog?.isShowing shouldBe true
    }

    @Test
    fun `dialog fragment with saved state skips`() {
        val controller = resumed()
        controller.pause().stop()
        val manager = controller.get().supportFragmentManager

        TestDialogFragment().showOnce(manager, TAG).shouldBeFalse()
        mainLooper.idle()

        manager.findFragmentByTag(TAG).shouldBeNull()
    }

    @Test
    fun `started dialog fragment without a dialog counts as gone`() {
        val manager = resumed().get().supportFragmentManager
        manager.beginTransaction().add(android.R.id.content, TestDialogFragment(), TAG).commitNow()
        val second = TestDialogFragment()

        second.showOnce(manager, TAG).shouldBeTrue()
        mainLooper.idle()

        second.dialog?.isShowing shouldBe true
    }

    // ── Dialog ─────────────────────────────────────────────────────────────────

    @Test
    fun `dialog with a free tag shows`() {
        val dialog = Dialog(resumed().get())

        dialog.showOnce(TAG).shouldBeTrue()

        dialog.isShowing.shouldBeTrue()
    }

    @Test
    fun `dialog with a showing tag skips`() {
        val activity = resumed().get()
        Dialog(activity).showOnce(TAG)
        val second = Dialog(activity)

        second.showOnce(TAG).shouldBeFalse()

        second.isShowing.shouldBeFalse()
    }

    @Test
    fun `dialog with a dismissed tag shows`() {
        val activity = resumed().get()
        val first = Dialog(activity).apply { showOnce(TAG) }
        first.dismiss()
        val second = Dialog(activity)

        second.showOnce(TAG).shouldBeTrue()

        second.isShowing.shouldBeTrue()
    }

    @Test
    fun `dialog with a collected tag shows`() {
        val activity = resumed().get()
        val first = showAndDismiss(activity)
        ShadowDialog.reset()
        mainLooper.idle()
        repeat(GC_ATTEMPTS) { if (first.get() != null) System.gc() }
        first.get().shouldBeNull()
        val second = Dialog(activity)

        second.showOnce(TAG).shouldBeTrue()

        second.isShowing.shouldBeTrue()
    }

    @Test
    fun `dialog without an activity shows unguarded`() {
        val app = RuntimeEnvironment.getApplication()
        val first = Dialog(app)
        val second = Dialog(app)

        first.showOnce(TAG).shouldBeTrue()
        second.showOnce(TAG).shouldBeTrue()

        second.isShowing.shouldBeTrue()
    }

    // ── AlertDialog.Builder ────────────────────────────────────────────────────

    @Test
    fun `builder shows once and returns the shown dialog`() {
        val activity = resumed().get()

        val first = AlertDialog.Builder(activity).setMessage("first").showOnce(TAG)
        val second = AlertDialog.Builder(activity).setMessage("second").showOnce(TAG)

        first.shouldNotBeNull().isShowing.shouldBeTrue()
        second.shouldBeNull()
    }

    @Test
    fun `terms of service dialog shows once per activity`() {
        val activity = resumed().get()

        activity.showTosDialogOnce().shouldNotBeNull()
        activity.showTosDialogOnce().shouldBeNull()

        ShadowDialog.getShownDialogs().size shouldBe 1
    }

    private fun resumed(): ActivityController<AppCompatActivity> =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .setup()
            .track(destroyActivities)

    private fun FragmentManager.dialogFragments(tag: String) = fragments.count { it.tag == tag }

    private fun showAndDismiss(activity: AppCompatActivity): WeakReference<Dialog> {
        val dialog = Dialog(activity)
        dialog.showOnce(TAG)
        dialog.dismiss()
        return WeakReference(dialog)
    }
}

/** Dialog fragment that reports its `onDestroy` to the test. */
class TestDialogFragment : DialogFragment() {
    var onDestroyAction: () -> Unit = {}

    override fun onDestroy() {
        super.onDestroy()
        onDestroyAction()
    }
}
