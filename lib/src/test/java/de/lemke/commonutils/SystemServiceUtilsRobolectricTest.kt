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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.setClip
import de.lemke.commonutils.ui.utils.showSoftInput
import de.lemke.commonutils.ui.utils.withSystemService
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemServiceUtilsRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `withSystemService runs the action on a registered service`() {
        ctx.withSystemService(ClipboardManager::class.java) { "found" } shouldBe "found"
    }

    @Test
    fun `withSystemService returns null for a class without a system service`() {
        ctx.withSystemService(String::class.java) { "found" } shouldBe null
    }

    @Test
    fun `withSystemService with onMissing runs the action on a registered service`() {
        ctx.withSystemService(ClipboardManager::class.java, onMissing = { "missing" }) { "found" } shouldBe "found"
    }

    @Test
    fun `withSystemService with onMissing returns the fallback for a class without a system service`() {
        ctx.withSystemService(String::class.java, onMissing = { "missing" }) { "found" } shouldBe "missing"
    }

    @Test
    fun `showSoftInput shows the soft keyboard`() {
        val imm = ctx.getSystemService(InputMethodManager::class.java)
        shadowOf(imm).isSoftInputVisible shouldBe false

        View(ctx).showSoftInput()

        shadowOf(imm).isSoftInputVisible shouldBe true
    }

    @Test
    fun `setClip sets the primary clip`() {
        ctx.setClip(ClipData.newPlainText("label", "x"))

        val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip
        clip?.getItemAt(0)?.text shouldBe "x"
        clip?.description?.label shouldBe "label"
    }
}
