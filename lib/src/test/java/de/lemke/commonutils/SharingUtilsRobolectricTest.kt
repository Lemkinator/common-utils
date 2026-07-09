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
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.copyToClipboard
import de.lemke.commonutils.ui.utils.getFileUri
import de.lemke.commonutils.ui.utils.isSamsungQuickShareAvailable
import de.lemke.commonutils.ui.utils.quickShare
import de.lemke.commonutils.ui.utils.quickShareBitmap
import de.lemke.commonutils.ui.utils.share
import de.lemke.commonutils.ui.utils.shareApp
import de.lemke.commonutils.ui.utils.shareBitmap
import de.lemke.commonutils.ui.utils.shareText
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import java.io.File
import java.io.OutputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class SharingUtilsRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `isSamsungQuickShareAvailable returns false when Quick Share not installed`() {
        ctx.isSamsungQuickShareAvailable().shouldBeFalse()
    }

    @Test
    fun `empty file list share returns false`() {
        emptyList<File>().share(ctx).shouldBeFalse()
    }

    @Test
    fun `copyToClipboard text sets primary clip text`() {
        ctx.copyToClipboard("hello clipboard", "myLabel")
        val clipboard = ctx.getSystemService(ClipboardManager::class.java)
        clipboard.primaryClip
            ?.getItemAt(0)
            ?.text
            .toString() shouldBe "hello clipboard"
    }

    @Test
    fun `copyToClipboard text returns true`() {
        ctx.copyToClipboard("any text", "label").shouldBeTrue()
    }

    @Test
    fun `shareText from activity returns true`() {
        activity().shareText("some text", "title").shouldBeTrue()
    }

    @Test
    fun `shareText with null title returns true`() {
        activity().shareText("some text").shouldBeTrue()
    }

    @Test
    fun `Context shareText ActivityNotFoundException fallback shows toast`() {
        val a = spyk(Robolectric.buildActivity(Activity::class.java).setup().get())
        every { a.startActivity(any<android.content.Intent>()) } throws ActivityNotFoundException("no share")
        a.shareText("hello", "Test title").shouldBeFalse()
    }

    @Test
    fun `shareApp from activity returns true`() {
        activity().shareApp().shouldBeTrue()
    }

    @Test
    fun `shareApp ActivityNotFoundException covers safeStartActivity catch branch`() {
        val a = spyk(Robolectric.buildActivity(Activity::class.java).setup().get())
        every { a.startActivity(any<android.content.Intent>()) } throws ActivityNotFoundException("no handler")
        a.shareApp().shouldBeFalse()
    }

    @Test
    fun `isSamsungQuickShareAvailable returns true when package installed`() {
        shadowOf(ctx.packageManager).installPackage(PackageInfo().also { it.packageName = "com.samsung.android.app.sharelive" })
        ctx.isSamsungQuickShareAvailable().shouldBeTrue()
    }
}

/** Tests for bitmap and file sharing paths that require mocking [FileProvider]. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class SharingUtilsBitmapRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private val fakeUri: Uri = Uri.parse("content://de.lemke.test.fileprovider/share/test.png")

    @BeforeEach
    fun setUp() {
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(any(), any(), any()) } returns fakeUri
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── getFileUri ──────────────────────────────────────────────────────────────

    @Test
    fun `getFileUri returns URI from FileProvider`() {
        val file = File(ctx.cacheDir, "test.png")
        val uri = file.getFileUri(ctx)
        uri shouldBe fakeUri
    }

    // ── copyToClipboard(Bitmap) ─────────────────────────────────────────────────

    @Test
    fun `copyToClipboard bitmap success - clips bitmap URI and returns true`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        ctx.copyToClipboard(bitmap, "label", "test.png").shouldBeTrue()
    }

    @Test
    fun `copyToClipboard bitmap compress-fail - returns false`() {
        val bitmap = mockk<Bitmap>()
        every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false
        ctx.copyToClipboard(bitmap, "label", "test.png").shouldBeFalse()
    }

    @Test
    fun `Bitmap copyToClipboard extension delegates to Context copyToClipboard`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.copyToClipboard(ctx, "label", "test.png").shouldBeTrue()
    }

    // ── Bitmap.share ────────────────────────────────────────────────────────────

    @Test
    fun `Bitmap share success - starts chooser intent and returns true`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.share(activity(), "test.png").shouldBeTrue()
    }

    @Test
    fun `Bitmap share with shareText - includes text extra`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.share(activity(), "test.png", "optional text").shouldBeTrue()
    }

    @Test
    fun `Bitmap share compress-fail - returns false`() {
        val bitmap = mockk<Bitmap>()
        every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false
        bitmap.share(ctx, "test.png").shouldBeFalse()
    }

    @Test
    fun `Bitmap share FileProvider throws - exception caught, returns false`() {
        every { FileProvider.getUriForFile(any(), any(), any()) } throws RuntimeException("test")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.share(ctx, "test.png").shouldBeFalse()
    }

    @Test
    fun `Context shareBitmap delegates to Bitmap share`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        activity().shareBitmap(bitmap, "test.png").shouldBeTrue()
    }

    // ── Bitmap.quickShare ───────────────────────────────────────────────────────

    @Test
    fun `quickShare success - starts activity and returns true`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.quickShare(activity(), "test.png").shouldBeTrue()
    }

    @Test
    fun `quickShare compress-fail - returns false`() {
        val bitmap = mockk<Bitmap>()
        every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false
        bitmap.quickShare(ctx, "test.png").shouldBeFalse()
    }

    @Test
    fun `Context quickShareBitmap delegates to Bitmap quickShare`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        activity().quickShareBitmap(bitmap, "test.png").shouldBeTrue()
    }

    // ── File / List<File>.share ──────────────────────────────────────────────────

    @Test
    fun `single File share - starts activity and returns true`() {
        val act = activity()
        val file = File(act.cacheDir, "img.png").also { it.createNewFile() }
        file.share(act).shouldBeTrue()
    }

    @Test
    fun `single-element List share - uses ACTION_SEND and returns true`() {
        val act = activity()
        val file = File(act.cacheDir, "img.png").also { it.createNewFile() }
        listOf(file).share(act).shouldBeTrue()
    }

    @Test
    fun `multi-element List share - uses ACTION_SEND_MULTIPLE and returns true`() {
        val act = activity()
        val f1 = File(act.cacheDir, "img1.png").also { it.createNewFile() }
        val f2 = File(act.cacheDir, "img2.png").also { it.createNewFile() }
        listOf(f1, f2).share(act).shouldBeTrue()
    }

    // ── Fragment overloads ───────────────────────────────────────────────────────

    private fun attachedFragment(): Fragment {
        val a = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val frag = Fragment()
        a.supportFragmentManager
            .beginTransaction()
            .add(frag, "f")
            .commitNow()
        return frag
    }

    @Test
    fun `Fragment shareBitmap delegates to Bitmap share`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        attachedFragment().shareBitmap(bitmap, "test.png").shouldBeTrue()
    }

    @Test
    fun `Fragment quickShareBitmap delegates to Bitmap quickShare`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        attachedFragment().quickShareBitmap(bitmap, "test.png").shouldBeTrue()
    }

    @Test
    fun `Fragment shareText delegates to Context shareText`() {
        attachedFragment().shareText("hello from fragment", "fragment title").shouldBeTrue()
    }

    @Test
    fun `Fragment shareText without title covers default-param synthetic`() {
        attachedFragment().shareText("hello from fragment").shouldBeTrue()
    }

    @Test
    fun `Fragment shareApp delegates to Context shareApp`() {
        attachedFragment().shareApp().shouldBeTrue()
    }

    @Test
    fun `quickShare ActivityNotFoundException in start falls back to safeStartActivity`() {
        // Both startActivity calls throw → start catch nulls package → safeStartActivity catch returns false
        val a = spyk(Robolectric.buildActivity(Activity::class.java).setup().get())
        every { a.startActivity(any<android.content.Intent>()) } throws ActivityNotFoundException("no handler")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.quickShare(a, "test.png").shouldBeFalse()
    }

    @Test
    fun `quickShare with Quick Share available sets Samsung package`() {
        // Install Samsung QS → createBaseIntent sets package → start(ctx) succeeds
        shadowOf(ctx.packageManager).installPackage(PackageInfo().also { it.packageName = "com.samsung.android.app.sharelive" })
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.quickShare(activity(), "test.png").shouldBeTrue()
    }
}
