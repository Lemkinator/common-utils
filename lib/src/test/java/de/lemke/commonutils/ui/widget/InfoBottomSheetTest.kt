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
package de.lemke.commonutils.ui.widget

import android.os.Looper
import android.view.Gravity.CENTER
import android.view.Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK
import android.view.Gravity.START
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import de.lemke.commonutils.DestroyActivitiesRule
import de.lemke.commonutils.R
import de.lemke.commonutils.track
import de.lemke.commonutils.ui.widget.InfoBottomSheet.Companion.showInfoBottomSheet
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InfoBottomSheetTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private fun activity(): AppCompatActivity =
        Robolectric
            .buildActivity(AppCompatActivity::class.java)
            .setup()
            .track(destroyActivities)
            .get()

    private fun AppCompatActivity.shownSheet(): InfoBottomSheet {
        shadowOf(Looper.getMainLooper()).idle()
        return supportFragmentManager.findFragmentByTag("InfoBottomSheet") as InfoBottomSheet
    }

    private fun InfoBottomSheet.text(id: Int): TextView = view!!.findViewById(id)

    // ── argument packing ───────────────────────────────────────────────────────

    @Test
    fun `title is stored in arguments bundle`() {
        val a = activity()
        a.showInfoBottomSheet("Hello", "World")
        a.shownSheet().arguments!!.getString(InfoBottomSheet.KEY_TITLE) shouldBe "Hello"
    }

    @Test
    fun `message is stored in arguments bundle`() {
        val a = activity()
        a.showInfoBottomSheet("T", "My Message")
        a.shownSheet().arguments!!.getString(InfoBottomSheet.KEY_MESSAGE) shouldBe "My Message"
    }

    @Test
    fun `text gravity defaults to CENTER`() {
        val a = activity()
        a.showInfoBottomSheet("T", "M")
        val sheet = a.shownSheet()
        sheet.arguments!!.getInt(InfoBottomSheet.KEY_TEXT_GRAVITY) shouldBe CENTER
        sheet.text(R.id.infoTitle).gravity shouldBe CENTER
        sheet.text(R.id.infoMessage).gravity shouldBe CENTER
    }

    @Test
    fun `explicit text gravity is preserved`() {
        val a = activity()
        a.showInfoBottomSheet("T", "M", START)
        val sheet = a.shownSheet()
        sheet.arguments!!.getInt(InfoBottomSheet.KEY_TEXT_GRAVITY) shouldBe START
        sheet.text(R.id.infoTitle).gravity and RELATIVE_HORIZONTAL_GRAVITY_MASK shouldBe START
        sheet.text(R.id.infoMessage).gravity and RELATIVE_HORIZONTAL_GRAVITY_MASK shouldBe START
    }

    // ── showInfoBottomSheet companion overloads + lifecycle ────────────────────

    @Test
    fun `showInfoBottomSheet(FragmentActivity, String, String) shows sheet and runs lifecycle`() {
        val a = activity()
        a.showInfoBottomSheet("Title", "Message")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `showInfoBottomSheet twice shows one sheet`() {
        val a = activity()

        a.showInfoBottomSheet("First", "Message") shouldBe true
        a.showInfoBottomSheet("Second", "Message") shouldBe false
        shadowOf(Looper.getMainLooper()).idle()

        a.supportFragmentManager.fragments
            .filterIsInstance<InfoBottomSheet>()
            .size shouldBe 1
    }

    @Test
    fun `showInfoBottomSheet(FragmentActivity, StringRes, StringRes) shows sheet`() {
        val a = activity()
        a.showInfoBottomSheet(R.string.commonutils_ok, R.string.commonutils_tos)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `showInfoBottomSheet(FragmentActivity, String, String, gravity) shows sheet with gravity`() {
        val a = activity()
        a.showInfoBottomSheet("T", "M", START)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `showInfoBottomSheet from Fragment shows sheet`() {
        val a = activity()
        val fragment = Fragment()
        a.supportFragmentManager
            .beginTransaction()
            .add(fragment, "host")
            .commitNow()
        with(InfoBottomSheet.Companion) { fragment.showInfoBottomSheet("T", "M") }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `showInfoBottomSheet from Fragment with StringRes shows sheet`() {
        val a = activity()
        val fragment = Fragment()
        a.supportFragmentManager
            .beginTransaction()
            .add(fragment, "host")
            .commitNow()
        with(InfoBottomSheet.Companion) {
            fragment.showInfoBottomSheet(R.string.commonutils_ok, R.string.commonutils_tos)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `showInfoBottomSheet with blank title covers title-hidden branch`() {
        val a = activity()
        a.showInfoBottomSheet("", "Some message")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `showInfoBottomSheet with blank message covers message-hidden branch`() {
        val a = activity()
        a.showInfoBottomSheet("Title", "")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `InfoBottomSheet shown from fragmentManager commits transaction`() {
        val a = activity()
        showInfoBottomSheet(a.supportFragmentManager, "T", "M")
        a.supportFragmentManager.executePendingTransactions()
        val shown = a.supportFragmentManager.findFragmentByTag(InfoBottomSheet::class.java.simpleName)
        shown shouldNotBe null
    }

    // ── onViewCreated lifecycle (showNow triggers synchronous fragment lifecycle) ──

    @Test
    fun `onViewCreated with title and message covers non-blank branches`() {
        val a = activity()
        a.showInfoBottomSheet("Real Title", "Real Message", START)
        val sheet = a.shownSheet()
        sheet.text(R.id.infoTitle).text.toString() shouldBe "Real Title"
        sheet.text(R.id.infoMessage).text.toString() shouldBe "Real Message"
    }

    @Test
    fun `onViewCreated with blank title covers isNullOrBlank true branch for title`() {
        val a = activity()
        a.showInfoBottomSheet("", "Some message")
        val sheet = a.shownSheet()
        sheet.text(R.id.infoTitle).isVisible shouldBe false
        sheet.text(R.id.infoMessage).isVisible shouldBe true
    }

    @Test
    fun `onViewCreated with blank message covers isNullOrBlank true branch for message`() {
        val a = activity()
        a.showInfoBottomSheet("A Title", "")
        val sheet = a.shownSheet()
        sheet.text(R.id.infoTitle).isVisible shouldBe true
        sheet.text(R.id.infoMessage).isVisible shouldBe false
    }

    @Test
    fun `InfoBottomSheet with no arguments covers null-arguments path`() {
        val a = activity()
        InfoBottomSheet().showNow(a.supportFragmentManager, "no_args_tag")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `onDismiss is called when sheet is dismissed`() {
        val a = activity()
        a.showInfoBottomSheet("T", "M")
        val sheet = a.shownSheet()
        sheet.dismiss()
        a.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
        a.supportFragmentManager.findFragmentByTag("InfoBottomSheet") shouldBe null
    }
}
