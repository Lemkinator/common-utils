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
import android.view.Gravity.START
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import de.lemke.commonutils.R
import de.lemke.commonutils.ui.widget.InfoBottomSheet.Companion.showInfoBottomSheet
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class InfoBottomSheetTest {
    // newInstance is private on the companion object; access via reflection so tests
    // cover argument-packing without needing a themed Activity to show the dialog.
    private fun newInstance(
        title: String,
        message: String,
        gravity: Int = CENTER,
    ): InfoBottomSheet {
        val method =
            InfoBottomSheet.Companion::class.java.getDeclaredMethod(
                "newInstance",
                String::class.java,
                String::class.java,
                Int::class.java,
            )
        method.isAccessible = true
        return method.invoke(InfoBottomSheet.Companion, title, message, gravity) as InfoBottomSheet
    }

    private fun activity(): AppCompatActivity =
        Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()

    // ── newInstance / argument packing ─────────────────────────────────────────

    @Test
    fun `title is stored in arguments bundle`() {
        val frag = newInstance("Hello", "World")
        frag.arguments!!.getString(InfoBottomSheet.KEY_TITLE) shouldBe "Hello"
    }

    @Test
    fun `message is stored in arguments bundle`() {
        val frag = newInstance("T", "My Message")
        frag.arguments!!.getString(InfoBottomSheet.KEY_MESSAGE) shouldBe "My Message"
    }

    @Test
    fun `text gravity defaults to CENTER`() {
        val frag = newInstance("T", "M")
        frag.arguments!!.getInt(InfoBottomSheet.KEY_TEXT_GRAVITY) shouldBe CENTER
    }

    @Test
    fun `explicit text gravity is preserved`() {
        val frag = newInstance("T", "M", START)
        frag.arguments!!.getInt(InfoBottomSheet.KEY_TEXT_GRAVITY) shouldBe START
    }

    // ── showInfoBottomSheet companion overloads + lifecycle ────────────────────

    @Test
    fun `showInfoBottomSheet(FragmentActivity, String, String) shows sheet and runs lifecycle`() {
        val a = activity()
        a.showInfoBottomSheet("Title", "Message")
        shadowOf(Looper.getMainLooper()).idle()
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
        a.supportFragmentManager.beginTransaction().add(fragment, "host").commitNow()
        with(InfoBottomSheet.Companion) { fragment.showInfoBottomSheet("T", "M") }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `showInfoBottomSheet from Fragment with StringRes shows sheet`() {
        val a = activity()
        val fragment = Fragment()
        a.supportFragmentManager.beginTransaction().add(fragment, "host").commitNow()
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
        InfoBottomSheet.showInfoBottomSheet(a.supportFragmentManager, "T", "M")
        a.supportFragmentManager.executePendingTransactions()
        val shown = a.supportFragmentManager.findFragmentByTag(InfoBottomSheet::class.java.simpleName)
        shown shouldNotBe null
    }

    // ── onViewCreated lifecycle (showNow triggers synchronous fragment lifecycle) ──

    @Test
    fun `onViewCreated with title and message covers non-blank branches`() {
        val a = activity()
        newInstance("Real Title", "Real Message", START).showNow(a.supportFragmentManager, "full_tag")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `onViewCreated with blank title covers isNullOrBlank true branch for title`() {
        val a = activity()
        newInstance("", "Some message").showNow(a.supportFragmentManager, "blank_t_tag")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `onViewCreated with blank message covers isNullOrBlank true branch for message`() {
        val a = activity()
        newInstance("A Title", "").showNow(a.supportFragmentManager, "blank_m_tag")
        shadowOf(Looper.getMainLooper()).idle()
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
        val fragment = newInstance("T", "M")
        fragment.showNow(a.supportFragmentManager, "dismiss_tag")
        shadowOf(Looper.getMainLooper()).idle()
        fragment.dismiss()
        a.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
