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
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
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

/** Fragment subclass that always throws ActivityNotFoundException from startActivity. */
class ThrowingStartActivityFragment : Fragment() {
    override fun startActivity(intent: Intent) = throw ActivityNotFoundException("no locale settings")
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class OpenUtilsApi36Test {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `areAppLocalSettingsSupported returns true on API 33+`() {
        areAppLocalSettingsSupported().shouldBeTrue()
    }

    @Test
    fun `openApplicationSettings fires ACTION_APPLICATION_DETAILS_SETTINGS intent`() {
        ctx.openApplicationSettings().shouldBeTrue()
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        intent shouldNotBe null
        intent.action shouldBe Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    }

    @Test
    fun `openApp with tryLocalFirst false fires Play Store intent`() {
        ctx.openApp("com.example.nonexistent", tryLocalFirst = false).shouldBeTrue()
        shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `openApp with tryLocalFirst true and unknown package falls back to store`() {
        ctx.openApp("com.example.notinstalled", tryLocalFirst = true).shouldBeTrue()
        shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `openAppWithPackageNameOnStore all URIs fail returns false`() {
        val a = spyk(Robolectric.buildActivity(Activity::class.java).setup().get())
        every { a.startActivity(any<Intent>()) } throws ActivityNotFoundException("no store")
        a.openApp("com.example.pkg", tryLocalFirst = false).shouldBeFalse()
    }

    @Test
    fun `openApplicationSettings ActivityNotFoundException returns false`() {
        val a = spyk(Robolectric.buildActivity(Activity::class.java).setup().get())
        every { a.startActivity(any<Intent>()) } throws ActivityNotFoundException("no settings")
        a.openApplicationSettings().shouldBeFalse()
    }

    @Test
    fun `openApp tryLocalFirst with installed package launches app directly`() {
        // Make getLaunchIntentForPackage return non-null via mocked PM
        val spyCtx = spyk(ctx)
        val pm = spyk(ctx.packageManager)
        every { spyCtx.packageManager } returns pm
        val fakeIntent = Intent("android.intent.action.MAIN").setPackage("com.example.installed")
        every { pm.getLaunchIntentForPackage("com.example.installed") } returns fakeIntent
        spyCtx.openApp("com.example.installed", tryLocalFirst = true).shouldBeTrue()
    }

    @Test
    fun `openApp tryLocalFirst with installed package but startActivity throws ActivityNotFoundException returns false`() {
        val spyCtx = spyk(ctx)
        val pm = spyk(ctx.packageManager)
        every { spyCtx.packageManager } returns pm
        val fakeIntent = Intent("android.intent.action.MAIN").setPackage("com.example.installed")
        every { pm.getLaunchIntentForPackage("com.example.installed") } returns fakeIntent
        every { spyCtx.startActivity(any<Intent>()) } throws ActivityNotFoundException("no app")
        spyCtx.openApp("com.example.installed", tryLocalFirst = true).shouldBeFalse()
    }

    @Test
    fun `openAppLocaleSettings ActivityNotFoundException returns false`() {
        val a = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val frag = ThrowingStartActivityFragment()
        a.supportFragmentManager
            .beginTransaction()
            .add(frag, "test")
            .commitNow()
        frag.openAppLocaleSettings().shouldBeFalse()
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [32])
class OpenUtilsApi32Test {
    @Test
    fun `areAppLocalSettingsSupported returns false below API 33`() {
        areAppLocalSettingsSupported().shouldBeFalse()
    }
}
