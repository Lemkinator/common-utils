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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.every
import io.mockk.spyk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class URLUtilsRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `openURL returns false for null URL`() {
        ctx.openURL(null).shouldBeFalse()
    }

    @Test
    fun `openURL returns false for blank URL`() {
        ctx.openURL("   ").shouldBeFalse()
    }

    @Test
    fun `openURL returns false for empty URL`() {
        ctx.openURL("").shouldBeFalse()
    }

    @Test
    fun `openURL returns true for valid URL from Activity context`() {
        Robolectric
            .buildActivity(Activity::class.java)
            .setup()
            .get()
            .openURL("https://example.com")
            .shouldBeTrue()
    }

    @Test
    fun `openURL returns true for valid URL from Application context`() {
        ctx.openURL("https://example.com").shouldBeTrue()
    }

    @Test
    fun `openURL returns false when startActivity throws ActivityNotFoundException`() {
        val a = spyk(Robolectric.buildActivity(Activity::class.java).setup().get())
        every { a.startActivity(any<Intent>()) } throws ActivityNotFoundException("no browser")
        a.openURL("https://example.com").shouldBeFalse()
    }

    @Test
    fun `openURL returns false when startActivity throws generic Exception`() {
        val a = spyk(Robolectric.buildActivity(Activity::class.java).setup().get())
        every { a.startActivity(any<Intent>()) } throws RuntimeException("crash")
        a.openURL("https://example.com").shouldBeFalse()
    }
}
