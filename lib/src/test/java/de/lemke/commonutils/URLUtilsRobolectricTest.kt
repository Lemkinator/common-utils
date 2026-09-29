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
import android.os.FileUriExposedException
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.openURL
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.spyk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class URLUtilsRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

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
            .track(destroyActivities)
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
        val a =
            spyk(
                Robolectric
                    .buildActivity(Activity::class.java)
                    .setup()
                    .track(destroyActivities)
                    .get(),
            )
        every { a.startActivity(any<Intent>()) } throws ActivityNotFoundException("no browser")
        a.openURL("https://example.com").shouldBeFalse()
    }

    @Test
    fun `openURL returns false when startActivity throws SecurityException`() {
        val a =
            spyk(
                Robolectric
                    .buildActivity(Activity::class.java)
                    .setup()
                    .track(destroyActivities)
                    .get(),
            )
        every { a.startActivity(any<Intent>()) } throws SecurityException("not exported")
        a.openURL("https://example.com").shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe a.getString(R.string.commonutils_error_cant_open_url)
    }

    @Test
    fun `openURL of a file URL shows the cant-open toast when startActivity throws FileUriExposedException`() {
        val failing = StartActivityFailingContext(ctx, FileUriExposedException("file:///sdcard/page.html exposed beyond app"))
        failing.openURL("file:///sdcard/page.html").shouldBeFalse()
        failing.startedIntents
            .single()
            .data
            .toString() shouldBe "file:///sdcard/page.html"
        ShadowToast.getTextOfLatestToast() shouldBe "Error: URL could not be opened."
    }

    @Test
    fun `openURL shows the cant-open toast when startActivity throws IllegalStateException`() {
        val failing = StartActivityFailingContext(ctx, IllegalStateException("activity manager unavailable"))
        failing.openURL("https://example.com").shouldBeFalse()
        ShadowToast.getTextOfLatestToast() shouldBe "Error: URL could not be opened."
    }
}
