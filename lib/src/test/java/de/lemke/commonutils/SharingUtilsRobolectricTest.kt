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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.CacheFileKind
import de.lemke.commonutils.ui.utils.copyToClipboard
import de.lemke.commonutils.ui.utils.createBitmapClip
import de.lemke.commonutils.ui.utils.createBitmapShareUri
import de.lemke.commonutils.ui.utils.getFileUri
import de.lemke.commonutils.ui.utils.isSamsungQuickShareAvailable
import de.lemke.commonutils.ui.utils.quickShareBitmap
import de.lemke.commonutils.ui.utils.resolveCacheFile
import de.lemke.commonutils.ui.utils.share
import de.lemke.commonutils.ui.utils.shareApp
import de.lemke.commonutils.ui.utils.shareBitmap
import de.lemke.commonutils.ui.utils.shareText
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowClipboardManager
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowToast

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
    fun `resolveCacheFile resolves a plain filename inside the kind's cache directory`() {
        val file = ctx.resolveCacheFile(CacheFileKind.SHARE, "test.png")
        file.canonicalPath shouldBe File(ctx.cacheDir, "share/test.png").canonicalPath
    }

    @Test
    fun `resolveCacheFile resolves an empty name to the kind's cache directory itself`() {
        val file = ctx.resolveCacheFile(CacheFileKind.CLIPBOARD, "")
        file.canonicalPath shouldBe File(ctx.cacheDir, "clipboard").canonicalPath
    }

    @Test
    fun `resolveCacheFile rejects a name that escapes the kind's cache directory`() {
        shouldThrow<IllegalArgumentException> { ctx.resolveCacheFile(CacheFileKind.SHARE, "../evil.png") }
    }
}

private const val FILE_PROVIDER_AUTHORITY = "de.lemke.commonutils.test.fileprovider"
private const val CACHE_ROOT_URI = "content://$FILE_PROVIDER_AUTHORITY/cache_root"

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

    private fun bitmapWriting(content: String): Bitmap =
        mockk {
            every { compress(any(), any(), any<OutputStream>()) } answers {
                thirdArg<OutputStream>().write(content.toByteArray())
                true
            }
        }

    private fun Intent.readGrantFlag(): Int = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION

    private fun unwritableCacheFileName(directoryName: String): String {
        File(ctx.cacheDir, "$directoryName/directory.png").mkdirs()
        return "directory.png"
    }

    private fun latestToastIsShareNotSupported() =
        ShadowToast.getTextOfLatestToast() shouldBe "Error: Sharing content is not supported on your device."

    private fun installQuickShare() =
        shadowOf(ctx.packageManager).installPackage(PackageInfo().also { it.packageName = "com.samsung.android.app.sharelive" })

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

    // ── createBitmapClip / copyToClipboard(ClipData) ────────────────────────────

    @Test
    fun `createBitmapClip clips the bitmap's content uri as a PNG under the label`() =
        runTest {
            val clip = ctx.createBitmapClip(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "label", "test.png").shouldNotBeNull()

            clip.getItemAt(0).uri.toString() shouldBe "$CACHE_ROOT_URI/clipboard/test.png"
            clip.description.label shouldBe "label"
            clip.description.getMimeType(0) shouldBe "image/png"
        }

    @Test
    fun `createBitmapClip writes on the given IO dispatcher`() =
        runTest {
            val io = HeldDispatcher()
            val clip = async { ctx.createBitmapClip(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "label", "test.png", io) }
            runCurrent()

            clip.isCompleted.shouldBeFalse()
            File(ctx.cacheDir, "clipboard/test.png").exists().shouldBeFalse()

            io.held.removeFirst().run()
            clip
                .await()
                .shouldNotBeNull()
                .getItemAt(0)
                .uri
                .toString() shouldBe "$CACHE_ROOT_URI/clipboard/test.png"
            File(ctx.cacheDir, "clipboard/test.png").exists().shouldBeTrue()
        }

    @Test
    fun `copyToClipboard sets a created bitmap clip and confirms with a toast`() =
        runTest {
            val clip = ctx.createBitmapClip(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "label", "test.png")

            ctx.copyToClipboard(clip).shouldBeTrue()

            ctx
                .getSystemService(ClipboardManager::class.java)
                .primaryClip
                .shouldNotBeNull()
                .getItemAt(0)
                .uri
                .toString() shouldBe "$CACHE_ROOT_URI/clipboard/test.png"
            ShadowToast.getTextOfLatestToast() shouldBe "Copied to clipboard"
        }

    @Test
    fun `createBitmapClip returns null and deletes the cache file when the bitmap cannot be encoded`() =
        runTest {
            val bitmap = mockk<Bitmap>()
            every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false

            ctx.createBitmapClip(bitmap, "label", "test.png") shouldBe null
            File(ctx.cacheDir, "clipboard/test.png").exists().shouldBeFalse()
        }

    @Test
    fun `createBitmapClip returns null and deletes the cache file when encoding throws`() =
        runTest {
            val bitmap = mockk<Bitmap>()
            every { bitmap.compress(any(), any(), any<OutputStream>()) } throws IOException("disk full")

            ctx.createBitmapClip(bitmap, "label", "test.png") shouldBe null
            File(ctx.cacheDir, "clipboard").listFiles()!!.shouldBeEmpty()
        }

    @Test
    fun `createBitmapClip returns null and deletes the cache file without a FileProvider for the package`() =
        runTest {
            contextWithoutFileProvider().createBitmapClip(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "label", "test.png") shouldBe
                null
            File(ctx.cacheDir, "clipboard/test.png").exists().shouldBeFalse()
        }

    @Test
    fun `createBitmapClip logs a cache file it cannot delete`() =
        runTest {
            File(ctx.cacheDir, "clipboard/directory.png/child.png").mkdirs()

            ctx.createBitmapClip(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "label", "directory.png") shouldBe null

            ShadowLog.getLogsForTag("SharingUtils").map { it.type to it.msg } shouldContainExactly
                listOf(Log.WARN to "Could not delete ${File(ctx.cacheDir, "clipboard/directory.png").canonicalPath}")
        }

    @Test
    fun `createBitmapClip logs no failed delete for a cache file it never created`() =
        runTest {
            ctx.createBitmapClip(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "label", "missing/test.png") shouldBe null

            File(ctx.cacheDir, "clipboard/missing/test.png").exists().shouldBeFalse()
            ShadowLog.getLogsForTag("SharingUtils").shouldBeEmpty()
        }

    @Test
    fun `createBitmapClip returns null for an unwritable cache file`() =
        runTest {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

            ctx.createBitmapClip(bitmap, "label", unwritableCacheFileName("clipboard")) shouldBe null
        }

    @Test
    fun `createBitmapClip returns null for a file name that escapes the cache directory`() =
        runTest {
            ctx.createBitmapClip(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "label", "../evil.png") shouldBe null
            File(ctx.cacheDir, "evil.png").exists().shouldBeFalse()
        }

    @Test
    fun `copyToClipboard without a clip shows the share toast and leaves the clipboard empty`() {
        ctx.copyToClipboard(null).shouldBeFalse()

        latestToastIsShareNotSupported()
        ctx.getSystemService(ClipboardManager::class.java).hasPrimaryClip().shouldBeFalse()
    }

    @Test
    @Config(shadows = [ShadowFileProvider::class, ShadowDeniedClipboardManager::class])
    fun `copyToClipboard SecurityException from setPrimaryClip shows the share toast and returns false`() {
        ctx.copyToClipboard(ClipData.newPlainText("label", "text")).shouldBeFalse()

        latestToastIsShareNotSupported()
    }

    @Test
    fun `createBitmapClip keeps a pending share's file intact`() =
        runTest {
            val act = activity()
            val shareUri = act.createBitmapShareUri(bitmapWriting("share"), "test.png")
            act.shareBitmap(shareUri).shouldBeTrue()
            val pendingUri = Uri.parse(act.startedChooserTarget().streamUri())

            ctx.createBitmapClip(bitmapWriting("clipboard"), "label", "test.png").shouldNotBeNull()

            ctx.contentResolver
                .openInputStream(pendingUri)
                .shouldNotBeNull()
                .use { it.reader().readText() } shouldBe "share"
        }

    // ── createBitmapShareUri ────────────────────────────────────────────────────

    @Test
    fun `createBitmapShareUri returns the content uri of the written PNG`() =
        runTest {
            val uri = ctx.createBitmapShareUri(bitmapWriting("png"), "test.png").shouldNotBeNull()

            uri.toString() shouldBe "$CACHE_ROOT_URI/share/test.png"
            ctx.contentResolver
                .openInputStream(uri)
                .shouldNotBeNull()
                .use { it.reader().readText() } shouldBe "png"
        }

    @Test
    fun `createBitmapShareUri writes on the given IO dispatcher`() =
        runTest {
            val io = HeldDispatcher()
            val uri = async { ctx.createBitmapShareUri(bitmapWriting("png"), "test.png", io) }
            runCurrent()

            uri.isCompleted.shouldBeFalse()
            File(ctx.cacheDir, "share/test.png").exists().shouldBeFalse()

            io.held.removeFirst().run()
            uri.await().toString() shouldBe "$CACHE_ROOT_URI/share/test.png"
            File(ctx.cacheDir, "share/test.png").readText() shouldBe "png"
        }

    @Test
    fun `createBitmapShareUri returns null and deletes the cache file when the bitmap cannot be encoded`() =
        runTest {
            val bitmap = mockk<Bitmap>()
            every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false

            ctx.createBitmapShareUri(bitmap, "test.png") shouldBe null
            File(ctx.cacheDir, "share/test.png").exists().shouldBeFalse()
        }

    @Test
    fun `createBitmapShareUri returns null and deletes the written file without a FileProvider for the package`() =
        runTest {
            contextWithoutFileProvider().createBitmapShareUri(bitmapWriting("png"), "test.png") shouldBe null

            File(ctx.cacheDir, "share").listFiles()!!.shouldBeEmpty()
        }

    @Test
    fun `createBitmapShareUri returns null for an unwritable cache file`() =
        runTest {
            ctx.createBitmapShareUri(bitmapWriting("png"), unwritableCacheFileName("share")) shouldBe null
        }

    @Test
    fun `createBitmapShareUri returns null for a file name that escapes the cache directory`() =
        runTest {
            ctx.createBitmapShareUri(bitmapWriting("png"), "../evil.png") shouldBe null
            File(ctx.cacheDir, "evil.png").exists().shouldBeFalse()
        }

    // ── shareBitmap ─────────────────────────────────────────────────────────────

    @Test
    fun `shareBitmap sends the content uri with read permission through a chooser`() {
        val act = activity()

        act.shareBitmap(Uri.parse("$CACHE_ROOT_URI/share/test.png")).shouldBeTrue()

        val target = act.startedChooserTarget()
        target.action shouldBe Intent.ACTION_SEND
        target.type shouldBe "image/png"
        target.streamUri() shouldBe "$CACHE_ROOT_URI/share/test.png"
        target.clipData
            ?.getItemAt(0)
            ?.uri
            .toString() shouldBe "$CACHE_ROOT_URI/share/test.png"
        target.readGrantFlag() shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
        target.hasExtra(Intent.EXTRA_TEXT).shouldBeFalse()
    }

    @Test
    fun `shareBitmap with shareText includes the text extra`() {
        val act = activity()

        act.shareBitmap(Uri.parse("$CACHE_ROOT_URI/share/test.png"), "optional text").shouldBeTrue()

        act.startedChooserTarget().getStringExtra(Intent.EXTRA_TEXT) shouldBe "optional text"
    }

    @Test
    fun `shareBitmap without a uri shows the share toast and starts nothing`() {
        val act = activity()

        act.shareBitmap(null).shouldBeFalse()

        latestToastIsShareNotSupported()
        shadowOf(act).nextStartedActivity shouldBe null
    }

    @Test
    fun `shareBitmap SecurityException from startActivity shows the share toast and returns false`() {
        val failing = StartActivityFailingContext(ctx, SecurityException("chooser denied"))

        failing.shareBitmap(Uri.parse("$CACHE_ROOT_URI/share/test.png")).shouldBeFalse()

        failing.startedIntents.single().action shouldBe Intent.ACTION_CHOOSER
        latestToastIsShareNotSupported()
    }

    // ── quickShareBitmap ────────────────────────────────────────────────────────

    @Test
    fun `quickShareBitmap without Quick Share sends the content uri with read permission`() {
        val act = activity()

        act.quickShareBitmap(Uri.parse("$CACHE_ROOT_URI/share/test.png")).shouldBeTrue()

        val intent = shadowOf(act).nextStartedActivity.shouldNotBeNull()
        intent.action shouldBe Intent.ACTION_SEND
        intent.`package` shouldBe null
        intent.type shouldBe "image/png"
        intent.streamUri() shouldBe "$CACHE_ROOT_URI/share/test.png"
        intent.readGrantFlag() shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    @Test
    fun `quickShareBitmap with Quick Share sends the content uri to the Quick Share package`() {
        installQuickShare()
        val act = activity()

        act.quickShareBitmap(Uri.parse("$CACHE_ROOT_URI/share/test.png")).shouldBeTrue()

        val intent = shadowOf(act).nextStartedActivity.shouldNotBeNull()
        intent.`package` shouldBe "com.samsung.android.app.sharelive"
        intent.streamUri() shouldBe "$CACHE_ROOT_URI/share/test.png"
    }

    @Test
    fun `quickShareBitmap without a uri shows the share toast and starts nothing`() {
        val act = activity()

        act.quickShareBitmap(null).shouldBeFalse()

        latestToastIsShareNotSupported()
        shadowOf(act).nextStartedActivity shouldBe null
    }

    @Test
    fun `quickShareBitmap SecurityException from the explicit Quick Share start shows the share toast and returns false`() {
        installQuickShare()
        val failing = StartActivityFailingContext(ctx, SecurityException("Quick Share activity not exported"))

        failing.quickShareBitmap(Uri.parse("$CACHE_ROOT_URI/share/test.png")).shouldBeFalse()

        failing.startedIntents.single().`package` shouldBe "com.samsung.android.app.sharelive"
        latestToastIsShareNotSupported()
    }

    @Test
    fun `quickShareBitmap retries without the package and toasts when no activity handles either intent`() {
        installQuickShare()
        val failing = StartActivityFailingContext(ctx, ActivityNotFoundException("no handler"))

        failing.quickShareBitmap(Uri.parse("$CACHE_ROOT_URI/share/test.png")).shouldBeFalse()

        failing.startedIntents.map { it.`package` } shouldContainExactly listOf("com.samsung.android.app.sharelive", null)
        latestToastIsShareNotSupported()
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
        latestToastIsShareNotSupported()
    }

    @Test
    fun `List share SecurityException from startActivity shows the share toast and returns false`() {
        val failing = StartActivityFailingContext(ctx, SecurityException("chooser denied"))
        val file = File(ctx.cacheDir, "img.png").also { it.createNewFile() }
        listOf(file).share(failing).shouldBeFalse()
        failing.startedIntents.single().action shouldBe Intent.ACTION_CHOOSER
        latestToastIsShareNotSupported()
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
    fun `Fragment createBitmapShareUri writes through the fragment's context`() =
        runTest {
            attachedFragment().createBitmapShareUri(bitmapWriting("png"), "test.png").toString() shouldBe
                "$CACHE_ROOT_URI/share/test.png"
        }

    @Test
    fun `Fragment shareBitmap starts the chooser from the fragment's activity`() {
        val fragment = attachedFragment()

        fragment.shareBitmap(Uri.parse("$CACHE_ROOT_URI/share/test.png")).shouldBeTrue()

        fragment.requireActivity().startedChooserTarget().streamUri() shouldBe "$CACHE_ROOT_URI/share/test.png"
    }

    @Test
    fun `Fragment quickShareBitmap starts the share from the fragment's activity`() {
        val fragment = attachedFragment()

        fragment.quickShareBitmap(Uri.parse("$CACHE_ROOT_URI/share/test.png")).shouldBeTrue()

        shadowOf(fragment.requireActivity()).nextStartedActivity.shouldNotBeNull().streamUri() shouldBe
            "$CACHE_ROOT_URI/share/test.png"
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
}

@Implements(ClipboardManager::class)
private class ShadowDeniedClipboardManager : ShadowClipboardManager() {
    @Implementation
    override fun setPrimaryClip(clip: ClipData): Unit = throw SecurityException("clipboard access denied")
}
