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

import android.view.Gravity.CENTER
import android.view.Gravity.START
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
}
