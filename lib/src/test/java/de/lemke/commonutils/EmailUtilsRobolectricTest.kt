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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.spyk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class EmailUtilsRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `sendEmail returns true and fires ACTION_SENDTO intent`() {
        ctx.sendEmail("test@example.com", "Hello", "Body text").shouldBeTrue()
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        intent shouldNotBe null
        intent.action shouldBe Intent.ACTION_SENDTO
    }

    @Test
    fun `sendEmail puts recipient address in EXTRA_EMAIL`() {
        ctx.sendEmail("dev@example.com", "Subject", "Body")
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        intent.getStringArrayExtra(Intent.EXTRA_EMAIL) shouldBe arrayOf("dev@example.com")
    }

    @Test
    fun `sendEmail puts subject in EXTRA_SUBJECT`() {
        ctx.sendEmail("a@b.com", "My Subject", "Body")
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        intent.getStringExtra(Intent.EXTRA_SUBJECT) shouldBe "My Subject"
    }

    @Test
    fun `sendEmail puts body in EXTRA_TEXT`() {
        ctx.sendEmail("a@b.com", "Subject", "My Body")
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        intent.getStringExtra(Intent.EXTRA_TEXT) shouldBe "My Body"
    }

    @Test
    fun `sendEmailHelp returns true and fires intent`() {
        ctx.sendEmailHelp("help@example.com", "Help Subject").shouldBeTrue()
        shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `sendEmailAboutMe returns true and fires intent`() {
        ctx.sendEmailAboutMe("me@example.com", "About Subject").shouldBeTrue()
        shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `sendEmail from Activity context does not add FLAG_ACTIVITY_NEW_TASK`() {
        // covers the `this@sendEmail IS Activity` branch (flag NOT added)
        Robolectric
            .buildActivity(Activity::class.java)
            .setup()
            .get()
            .sendEmail("a@b.com", "Sub", "Body")
            .shouldBeTrue()
    }

    @Test
    fun `sendEmail returns false on ActivityNotFoundException`() {
        val a = spyk(Robolectric.buildActivity(Activity::class.java).setup().get())
        every { a.startActivity(any<Intent>()) } throws ActivityNotFoundException("no email")
        a.sendEmail("a@b.com", "Sub", "Body").shouldBeFalse()
    }
}
