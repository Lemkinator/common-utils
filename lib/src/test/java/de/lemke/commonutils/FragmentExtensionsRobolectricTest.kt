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

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.data.commonUtilsSettings
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** Tests that Fragment overloads correctly delegate to their Context counterparts. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class FragmentExtensionsRobolectricTest {
    private lateinit var fragment: Fragment

    @BeforeEach
    fun setUp() {
        val prefs =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("frag_ext_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        commonUtilsSettings = SettingsRepository(prefs)

        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        fragment = Fragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(fragment, "testFrag")
            .commitNow()
    }

    // ── ToastUtils Fragment overloads ──────────────────────────────────────────

    @Test
    fun `Fragment toast(String) shows toast`() {
        fragment.toast("Fragment toast msg")
        ShadowToast.getTextOfLatestToast() shouldBe "Fragment toast msg"
    }

    @Test
    fun `Fragment toast(StringRes) shows toast`() {
        fragment.toast(R.string.commonutils_copied_to_clipboard)
        // Just verify it doesn't crash; ShadowToast records count.
        ShadowToast.shownToastCount() shouldBe 1
    }

    // ── URLUtils Fragment overload ─────────────────────────────────────────────

    @Test
    fun `Fragment openURL null returns false`() {
        fragment.openURL(null).shouldBeFalse()
    }

    @Test
    fun `Fragment openURL blank returns false`() {
        fragment.openURL("").shouldBeFalse()
    }

    @Test
    fun `Fragment openURL valid delegates to Context openURL`() {
        fragment.openURL("https://example.com").shouldBeTrue()
    }

    // ── EmailUtils Fragment overload ───────────────────────────────────────────

    @Test
    fun `Fragment sendEmailBugReport fires ACTION_SENDTO intent`() {
        fragment.sendEmailBugReport("bug@example.com", "Bug Subject")
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity.action shouldBe Intent.ACTION_SENDTO
    }

    // ── OpenUtils Fragment overloads ───────────────────────────────────────────

    @Test
    fun `Fragment openApp tryLocalFirst-false fires store intent`() {
        fragment.openApp("com.example.test", tryLocalFirst = false).shouldBeTrue()
    }

    @Test
    fun `Fragment openApp tryLocalFirst-true with unknown package falls back to store`() {
        fragment.openApp("com.example.notinstalled", tryLocalFirst = true).shouldBeTrue()
    }

    // ── SharingUtils Fragment overloads ───────────────────────────────────────

    @Test
    fun `Fragment shareApp delegates to Context shareApp`() {
        fragment.shareApp().shouldBeTrue()
    }

    @Test
    fun `Fragment shareText delegates to Context shareText`() {
        fragment.shareText("hello", "title").shouldBeTrue()
    }

    @Test
    fun `Fragment copyToClipboard delegates to Context copyToClipboard`() {
        fragment.copyToClipboard("clip text", "label").shouldBeTrue()
    }

    // ── ExportUtils Fragment overload ─────────────────────────────────────────

    @Test
    fun `Fragment exportBitmap with null launcher and CUSTOM returns false`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        fragment.exportBitmap(SaveLocation.CUSTOM, bitmap, "test", null).shouldBeFalse()
    }

    @Test
    fun `Fragment exportBitmap with non-null launcher and CUSTOM returns true`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val launcher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
        fragment.exportBitmap(SaveLocation.CUSTOM, bitmap, "test", launcher).shouldBeTrue()
    }
}

/** Tests [openAppLocaleSettings] Fragment overload - requires API 33+. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class OpenAppLocaleSettingsApi33Test {
    @Test
    fun `Fragment openAppLocaleSettings on API 33+ starts locale settings intent`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val fragment = Fragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(fragment, "tag")
            .commitNow()
        // SDK 36 ≥ TIRAMISU → should start ACTION_APP_LOCALE_SETTINGS
        fragment.openAppLocaleSettings().shouldBeTrue()
    }
}

/** Tests [openAppLocaleSettings] Fragment overload below API 33 - shows error toast and returns false. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [32])
class OpenAppLocaleSettingsApi32Test {
    @Test
    fun `Fragment openAppLocaleSettings below API 33 returns false`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val fragment = Fragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(fragment, "tag")
            .commitNow()
        fragment.openAppLocaleSettings().shouldBeFalse()
    }
}
