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
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.copyToClipboard
import de.lemke.commonutils.ui.utils.getFileUri
import de.lemke.commonutils.ui.utils.isSamsungQuickShareAvailable
import de.lemke.commonutils.ui.utils.quickShare
import de.lemke.commonutils.ui.utils.quickShareBitmap
import de.lemke.commonutils.ui.utils.resolveShareCacheFile
import de.lemke.commonutils.ui.utils.share
import de.lemke.commonutils.ui.utils.shareApp
import de.lemke.commonutils.ui.utils.shareBitmap
import de.lemke.commonutils.ui.utils.shareText
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import java.io.File
import java.io.OutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharingUtilsRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun activity(): Activity =
        Robolectric
            .buildActivity(Activity::class.java)
            .setup()
            .track(destroyActivities)
            .get()

    @Test
    fun `isSamsungQuickShareAvailable returns false when Quick Share not installed`() {
        ctx.isSamsungQuickShareAvailable().shouldBeFalse()
    }

    @Test
    fun `empty file list share returns false`() {
        emptyList<File>().share(ctx).shouldBeFalse()
    }

    @Test
    fun `copyToClipboard text sets primary clip text and returns true`() {
        ctx.copyToClipboard("hello clipboard", "myLabel").shouldBeTrue()
        val clipboard = ctx.getSystemService(ClipboardManager::class.java)
        clipboard.primaryClip
            ?.getItemAt(0)
            ?.text
            .toString() shouldBe "hello clipboard"
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
        val a =
            spyk(
                Robolectric
                    .buildActivity(Activity::class.java)
                    .setup()
                    .track(destroyActivities)
                    .get(),
            )
        every { a.startActivity(any<android.content.Intent>()) } throws ActivityNotFoundException("no share")
        a.shareText("hello", "Test title").shouldBeFalse()
    }

    @Test
    fun `shareApp from activity returns true`() {
        activity().shareApp().shouldBeTrue()
    }

    @Test
    fun `shareApp ActivityNotFoundException covers safeStartActivity catch branch`() {
        val a =
            spyk(
                Robolectric
                    .buildActivity(Activity::class.java)
                    .setup()
                    .track(destroyActivities)
                    .get(),
            )
        every { a.startActivity(any<android.content.Intent>()) } throws ActivityNotFoundException("no handler")
        a.shareApp().shouldBeFalse()
    }

    @Test
    fun `isSamsungQuickShareAvailable returns true when package installed`() {
        shadowOf(ctx.packageManager).installPackage(PackageInfo().also { it.packageName = "com.samsung.android.app.sharelive" })
        ctx.isSamsungQuickShareAvailable().shouldBeTrue()
    }

    @Test
    fun `resolveShareCacheFile resolves a plain filename inside cacheDir`() {
        val file = ctx.resolveShareCacheFile("test.png")
        file.parentFile?.canonicalPath shouldBe ctx.cacheDir.canonicalPath
    }

    @Test
    fun `resolveShareCacheFile resolves an empty name to cacheDir itself`() {
        val file = ctx.resolveShareCacheFile("")
        file.canonicalPath shouldBe ctx.cacheDir.canonicalPath
    }

    @Test
    fun `resolveShareCacheFile rejects a name that escapes cacheDir`() {
        shouldThrow<IllegalArgumentException> { ctx.resolveShareCacheFile("../evil.png") }
    }
}

private const val FILE_PROVIDER_AUTHORITY = "de.lemke.commonutils.test.fileprovider"
private const val CACHE_ROOT_URI = "content://$FILE_PROVIDER_AUTHORITY/cache_root"

/** Bitmap and file sharing through the test manifest's FileProvider, resolved by [ShadowFileProvider]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowFileProvider::class])
class SharingUtilsBitmapRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun activity(): Activity =
        Robolectric
            .buildActivity(Activity::class.java)
            .setup()
            .track(destroyActivities)
            .get()

    private fun contextWithoutFileProvider(): Context =
        object : ContextWrapper(ctx) {
            override fun getPackageName() = "de.lemke.commonutils.noprovider"
        }

    private fun Activity.startedChooserTarget(): Intent {
        val chooser = shadowOf(this).nextStartedActivity.shouldNotBeNull()
        chooser.action shouldBe Intent.ACTION_CHOOSER
        return IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java).shouldNotBeNull()
    }

    private fun Intent.streamUri(): String = IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java).toString()

    private fun Intent.readGrantFlag(): Int = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION

    // ── getFileUri ──────────────────────────────────────────────────────────────

    @Test
    fun `getFileUri maps a cache file to its cache_root content uri`() {
        File(ctx.cacheDir, "test.png").getFileUri(ctx).toString() shouldBe "$CACHE_ROOT_URI/test.png"
    }

    @Test
    fun `getFileUri throws IllegalArgumentException for a file outside every configured root`() {
        val outside = File(ctx.dataDir, "outside.png").also { it.createNewFile() }
        shouldThrow<IllegalArgumentException> { outside.getFileUri(ctx) }
    }

    // ── copyToClipboard(Bitmap) ─────────────────────────────────────────────────

    @Test
    fun `copyToClipboard bitmap success - clips the bitmap's content uri and returns true`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        ctx.copyToClipboard(bitmap, "label", "test.png").shouldBeTrue()
        val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip.shouldNotBeNull()
        clip.getItemAt(0).uri.toString() shouldBe "$CACHE_ROOT_URI/test.png"
        clip.description.getMimeType(0) shouldBe "image/png"
    }

    @Test
    fun `copyToClipboard bitmap compress-fail - returns false`() {
        val bitmap = mockk<Bitmap>()
        every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false
        ctx.copyToClipboard(bitmap, "label", "test.png").shouldBeFalse()
    }

    @Test
    fun `copyToClipboard bitmap without a FileProvider for the package - exception caught, returns false`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        contextWithoutFileProvider().copyToClipboard(bitmap, "label", "test.png").shouldBeFalse()
        ctx.getSystemService(ClipboardManager::class.java).hasPrimaryClip().shouldBeFalse()
    }

    @Test
    fun `copyToClipboard bitmap rejects shareFileName that escapes cacheDir`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        ctx.copyToClipboard(bitmap, "label", "../evil.png").shouldBeFalse()
    }

    @Test
    fun `Bitmap copyToClipboard extension delegates to Context copyToClipboard`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.copyToClipboard(ctx, "label", "test.png").shouldBeTrue()
    }

    // ── Bitmap.share ────────────────────────────────────────────────────────────

    @Test
    fun `Bitmap share success - sends the bitmap's content uri with read permission through a chooser`() {
        val act = activity()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.share(act, "test.png").shouldBeTrue()
        val target = act.startedChooserTarget()
        target.action shouldBe Intent.ACTION_SEND
        target.streamUri() shouldBe "$CACHE_ROOT_URI/test.png"
        target.clipData
            ?.getItemAt(0)
            ?.uri
            .toString() shouldBe "$CACHE_ROOT_URI/test.png"
        target.readGrantFlag() shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    @Test
    fun `Bitmap share with shareText - includes text extra`() {
        val act = activity()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.share(act, "test.png", "optional text").shouldBeTrue()
        act.startedChooserTarget().getStringExtra(Intent.EXTRA_TEXT) shouldBe "optional text"
    }

    @Test
    fun `Bitmap share compress-fail - returns false`() {
        val bitmap = mockk<Bitmap>()
        every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false
        bitmap.share(ctx, "test.png").shouldBeFalse()
    }

    @Test
    fun `Bitmap share without a FileProvider for the package - exception caught, returns false`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.share(contextWithoutFileProvider(), "test.png").shouldBeFalse()
    }

    @Test
    fun `Bitmap share rejects shareFileName that escapes cacheDir`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.share(ctx, "../evil.png").shouldBeFalse()
    }

    @Test
    fun `Context shareBitmap delegates to Bitmap share`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        activity().shareBitmap(bitmap, "test.png").shouldBeTrue()
    }

    // ── Bitmap.quickShare ───────────────────────────────────────────────────────

    @Test
    fun `quickShare success - sends the bitmap's content uri with read permission and returns true`() {
        val act = activity()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.quickShare(act, "test.png").shouldBeTrue()
        val intent = shadowOf(act).nextStartedActivity.shouldNotBeNull()
        intent.action shouldBe Intent.ACTION_SEND
        intent.streamUri() shouldBe "$CACHE_ROOT_URI/test.png"
        intent.readGrantFlag() shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    @Test
    fun `quickShare compress-fail - returns false`() {
        val bitmap = mockk<Bitmap>()
        every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false
        bitmap.quickShare(ctx, "test.png").shouldBeFalse()
    }

    @Test
    fun `quickShare without a FileProvider for the package - exception caught, returns false`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.quickShare(contextWithoutFileProvider(), "test.png").shouldBeFalse()
    }

    @Test
    fun `quickShare rejects shareFileName that escapes cacheDir`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.quickShare(ctx, "../evil.png").shouldBeFalse()
    }

    @Test
    fun `Context quickShareBitmap delegates to Bitmap quickShare`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        activity().quickShareBitmap(bitmap, "test.png").shouldBeTrue()
    }

    // ── File / List<File>.share ──────────────────────────────────────────────────

    @Test
    fun `single File share - sends the file's content uri with read permission and returns true`() {
        val act = activity()
        val file = File(act.cacheDir, "img.png").also { it.createNewFile() }
        file.share(act).shouldBeTrue()
        val target = act.startedChooserTarget()
        target.streamUri() shouldBe "$CACHE_ROOT_URI/img.png"
        target.readGrantFlag() shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    @Test
    fun `single-element List share - uses ACTION_SEND and returns true`() {
        val act = activity()
        val file = File(act.cacheDir, "img.png").also { it.createNewFile() }
        listOf(file).share(act).shouldBeTrue()
        act.startedChooserTarget().action shouldBe Intent.ACTION_SEND
    }

    @Test
    fun `multi-element List share - sends every content uri with ACTION_SEND_MULTIPLE and returns true`() {
        val act = activity()
        val f1 = File(act.cacheDir, "img1.png").also { it.createNewFile() }
        val f2 = File(act.cacheDir, "img2.png").also { it.createNewFile() }
        listOf(f1, f2).share(act).shouldBeTrue()
        val target = act.startedChooserTarget()
        target.action shouldBe Intent.ACTION_SEND_MULTIPLE
        IntentCompat.getParcelableArrayListExtra(target, Intent.EXTRA_STREAM, Uri::class.java)?.map(Uri::toString) shouldBe
            listOf("$CACHE_ROOT_URI/img1.png", "$CACHE_ROOT_URI/img2.png")
        target.readGrantFlag() shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    @Test
    fun `List share of a file outside every configured root - exception caught, returns false`() {
        val act = activity()
        val outside = File(act.dataDir, "outside.png").also { it.createNewFile() }
        outside.share(act).shouldBeFalse()
        shadowOf(act).nextStartedActivity shouldBe null
    }

    @Test
    fun `File share ActivityNotFoundException returns false`() {
        val a =
            spyk(
                Robolectric
                    .buildActivity(Activity::class.java)
                    .setup()
                    .track(destroyActivities)
                    .get(),
            )
        every { a.startActivity(any<Intent>()) } throws ActivityNotFoundException("no handler")
        val file = File(a.cacheDir, "img.png").also { it.createNewFile() }
        file.share(a).shouldBeFalse()
    }

    @Test
    fun `List share wraps the intent in a chooser even when Quick Share is installed`() {
        shadowOf(ctx.packageManager).installPackage(PackageInfo().also { it.packageName = "com.samsung.android.app.sharelive" })
        val a =
            spyk(
                Robolectric
                    .buildActivity(Activity::class.java)
                    .setup()
                    .track(destroyActivities)
                    .get(),
            )
        val intentSlot = slot<Intent>()
        every { a.startActivity(capture(intentSlot)) } just Runs
        val file = File(a.cacheDir, "img.png").also { it.createNewFile() }
        file.share(a).shouldBeTrue()
        intentSlot.captured.action shouldBe Intent.ACTION_CHOOSER
    }

    // ── Fragment overloads ───────────────────────────────────────────────────────

    private fun attachedFragment(): Fragment {
        val a =
            Robolectric
                .buildActivity(AppCompatActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
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
        val a =
            spyk(
                Robolectric
                    .buildActivity(Activity::class.java)
                    .setup()
                    .track(destroyActivities)
                    .get(),
            )
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
