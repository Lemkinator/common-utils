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
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.commonutils.ui.utils.CACHE_WRITE_RETENTION
import de.lemke.commonutils.ui.utils.CacheFileKind
import de.lemke.commonutils.ui.utils.LaunchOutcome
import de.lemke.commonutils.ui.utils.copyToClipboard
import de.lemke.commonutils.ui.utils.createBitmapClip
import de.lemke.commonutils.ui.utils.createBitmapShareFile
import de.lemke.commonutils.ui.utils.createCacheWriteDirectory
import de.lemke.commonutils.ui.utils.deleteExpiredCacheWrites
import de.lemke.commonutils.ui.utils.deleteOrLog
import de.lemke.commonutils.ui.utils.getFileUri
import de.lemke.commonutils.ui.utils.isSamsungQuickShareAvailable
import de.lemke.commonutils.ui.utils.quickShareBitmap
import de.lemke.commonutils.ui.utils.share
import de.lemke.commonutils.ui.utils.shareApp
import de.lemke.commonutils.ui.utils.shareBitmap
import de.lemke.commonutils.ui.utils.shareText
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeNoException
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
    fun `empty file list share shows the share toast and returns Failed`() {
        emptyList<File>().share(ctx) shouldBe LaunchOutcome.Failed
        ShadowToast.getTextOfLatestToast() shouldBe "Error: Sharing content is not supported on your device."
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
    fun `shareText from activity returns Started`() {
        activity().shareText("some text", "title") shouldBe LaunchOutcome.Started
    }

    @Test
    fun `shareText with null title returns Started`() {
        activity().shareText("some text") shouldBe LaunchOutcome.Started
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
        a.shareText("hello", "Test title") shouldBe LaunchOutcome.Failed
    }

    @Test
    fun `shareApp from activity returns Started`() {
        activity().shareApp() shouldBe LaunchOutcome.Started
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
        a.shareApp() shouldBe LaunchOutcome.Failed
    }

    @Test
    fun `isSamsungQuickShareAvailable returns true when package installed`() {
        shadowOf(ctx.packageManager).installPackage(PackageInfo().also { it.packageName = "com.samsung.android.app.sharelive" })
        ctx.isSamsungQuickShareAvailable().shouldBeTrue()
    }

    @Test
    fun `createCacheWriteDirectory creates a new directory inside the kind's cache directory for every write`() {
        val first = ctx.createCacheWriteDirectory(CacheFileKind.SHARE).root
        val second = ctx.createCacheWriteDirectory(CacheFileKind.SHARE).root

        first shouldNotBe second
        listOf(first, second).map { it.parentFile?.canonicalPath } shouldContainExactly
            List(2) { File(ctx.cacheDir, "share").canonicalPath }
        listOf(first, second).map { it.isDirectory } shouldContainExactly listOf(true, true)
    }

    @Test
    fun `createCacheWriteDirectory names every write directory with the library prefix`() {
        ctx.createCacheWriteDirectory(CacheFileKind.SHARE).root.name shouldStartWith "commonutils-"
    }

    @Test
    fun `deleteExpiredCacheWrites deletes an expired write directory and keeps a younger one`() {
        val now = System.currentTimeMillis()
        val expired = ctx.createCacheWriteDirectory(CacheFileKind.SHARE).root
        val recent = ctx.createCacheWriteDirectory(CacheFileKind.SHARE).root
        File(expired, "test.png").writeText("expired")
        File(recent, "test.png").writeText("recent")
        expired.setLastModified(now - CACHE_WRITE_RETENTION.inWholeMilliseconds - 60_000).shouldBeTrue()
        recent.setLastModified(now - CACHE_WRITE_RETENTION.inWholeMilliseconds + 60_000).shouldBeTrue()

        ctx.deleteExpiredCacheWrites(CacheFileKind.SHARE, now)

        File(ctx.cacheDir, "share").list()!!.asList() shouldContainExactly listOf(recent.name)
        File(recent, "test.png").readText() shouldBe "recent"
    }

    @Test
    fun `deleteExpiredCacheWrites keeps expired foreign entries in the kind's cache directory`() {
        val shareDirectory = File(ctx.cacheDir, "share")
        val foreignFile = File(shareDirectory, "icon.png").apply { parentFile?.mkdirs() }
        foreignFile.writeText("foreign")
        foreignFile.setLastModified(0).shouldBeTrue()
        val foreignDirectory = File(shareDirectory, "exports").apply { mkdirs() }
        foreignDirectory.setLastModified(0).shouldBeTrue()
        val prefixedFile = File(shareDirectory, "commonutils-notes.txt")
        prefixedFile.writeText("prefixed")
        prefixedFile.setLastModified(0).shouldBeTrue()

        ctx.deleteExpiredCacheWrites(CacheFileKind.SHARE, System.currentTimeMillis())

        shareDirectory.list()!!.asList() shouldContainExactlyInAnyOrder listOf("icon.png", "exports", "commonutils-notes.txt")
        foreignFile.readText() shouldBe "foreign"
        prefixedFile.readText() shouldBe "prefixed"
    }

    @Test
    fun `deleteExpiredCacheWrites keeps a prefixed symbolic link and the files of its target`() {
        val target = File(ctx.cacheDir, "target").apply { mkdirs() }
        val targetFile = File(target, "test.png").apply { writeText("target") }
        target.setLastModified(0).shouldBeTrue()
        val link = File(ctx.cacheDir, "share/commonutils-link").toPath()
        Files.createDirectories(link.parent)
        try {
            Files.createSymbolicLink(link, target.toPath())
        } catch (e: IOException) {
            // Windows refuses symbolic links without the SeCreateSymbolicLinkPrivilege or developer mode.
            assumeNoException(e)
        }

        ctx.deleteExpiredCacheWrites(CacheFileKind.SHARE, System.currentTimeMillis())

        Files.isSymbolicLink(link).shouldBeTrue()
        targetFile.readText() shouldBe "target"
    }

    @Test
    fun `deleteExpiredCacheWrites leaves the other kind's write directories alone`() {
        val clip = ctx.createCacheWriteDirectory(CacheFileKind.CLIPBOARD).root
        File(clip, "test.png").writeText("clip")
        clip.setLastModified(0).shouldBeTrue()

        ctx.deleteExpiredCacheWrites(CacheFileKind.SHARE, System.currentTimeMillis())

        File(clip, "test.png").readText() shouldBe "clip"
    }

    @Test
    fun `deleteOrLog logs a directory it cannot delete`() {
        val directory = File(ctx.cacheDir, "directory").apply { File(this, "child").mkdirs() }

        directory.deleteOrLog()

        directory.exists().shouldBeTrue()
        ShadowLog.getLogsForTag("SharingUtils").map { it.type to it.msg } shouldContainExactly
            listOf(Log.WARN to "Could not delete ${directory.path}")
    }

    @Test
    fun `deleteOrLog logs nothing for a missing file`() {
        File(ctx.cacheDir, "missing.png").deleteOrLog()

        ShadowLog.getLogsForTag("SharingUtils").shouldBeEmpty()
    }

    @Test
    fun `CacheWriteDirectory resolves a plain filename inside itself`() {
        val directory = ctx.createCacheWriteDirectory(CacheFileKind.SHARE)

        directory.resolve("test.png").canonicalPath shouldBe File(directory.root, "test.png").canonicalPath
    }

    @Test
    fun `CacheWriteDirectory rejects an empty name that resolves to the directory itself`() {
        shouldThrow<IllegalArgumentException> { ctx.createCacheWriteDirectory(CacheFileKind.SHARE).resolve("") }
    }

    @Test
    fun `CacheWriteDirectory rejects a name that escapes it`() {
        shouldThrow<IllegalArgumentException> { ctx.createCacheWriteDirectory(CacheFileKind.SHARE).resolve("../evil.png") }
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

    private fun blockCacheDirectory(directoryName: String) = File(ctx.cacheDir, directoryName).writeText("not a directory")

    private fun writtenUri(
        directoryName: String,
        fileName: String,
    ): String = "$CACHE_ROOT_URI/$directoryName/${ctx.cacheWriteDirectoryName(directoryName)}/$fileName"

    private fun Uri.readText(): String =
        ctx.contentResolver
            .openInputStream(this)
            .shouldNotBeNull()
            .use { it.reader().readText() }

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

            clip.getItemAt(0).uri.toString() shouldBe writtenUri("clipboard", "test.png")
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
            ctx.cacheWriteFiles("clipboard").shouldBeEmpty()

            io.held.removeFirst().run()
            clip
                .await()
                .shouldNotBeNull()
                .getItemAt(0)
                .uri
                .toString() shouldBe writtenUri("clipboard", "test.png")
            ctx.cacheWriteFiles("clipboard").map { it.name } shouldContainExactly listOf("test.png")
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
                .toString() shouldBe writtenUri("clipboard", "test.png")
            ShadowToast.getTextOfLatestToast() shouldBe "Copied to clipboard"
        }

    @Test
    fun `createBitmapClip returns null and deletes the cache file when the bitmap cannot be encoded`() =
        runTest {
            val bitmap = mockk<Bitmap>()
            every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false

            ctx.createBitmapClip(bitmap, "label", "test.png") shouldBe null
            File(ctx.cacheDir, "clipboard").listFiles()!!.shouldBeEmpty()
        }

    @Test
    fun `createBitmapClip returns null, deletes the cache file and logs the error when encoding throws`() =
        runTest {
            val error = IOException("disk full")
            val bitmap = mockk<Bitmap>()
            every { bitmap.compress(any(), any(), any<OutputStream>()) } throws error

            ctx.createBitmapClip(bitmap, "label", "test.png") shouldBe null
            File(ctx.cacheDir, "clipboard").listFiles()!!.shouldBeEmpty()
            val log = ShadowLog.getLogsForTag("ClipboardUtils").single()
            log.type to log.msg shouldBe (Log.ERROR to "Error writing bitmap clip")
            log.throwable shouldBeSameInstanceAs error
        }

    @Test
    fun `createBitmapClip returns null and deletes the cache file without a FileProvider for the package`() =
        runTest {
            contextWithoutFileProvider().createBitmapClip(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "label", "test.png") shouldBe
                null
            File(ctx.cacheDir, "clipboard").listFiles()!!.shouldBeEmpty()
        }

    @Test
    fun `createBitmapClip returns null and logs the error for a cache file it cannot create`() =
        runTest {
            ctx.createBitmapClip(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "label", "missing/test.png") shouldBe null

            File(ctx.cacheDir, "clipboard").listFiles()!!.shouldBeEmpty()
            val log = ShadowLog.getLogsForTag("ClipboardUtils").single()
            log.type to log.msg shouldBe (Log.ERROR to "Error writing bitmap clip")
            log.throwable.shouldBeInstanceOf<FileNotFoundException>()
        }

    @Test
    fun `createBitmapClip returns null when the clipboard cache directory cannot be created`() =
        runTest {
            blockCacheDirectory("clipboard")

            ctx.createBitmapClip(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "label", "test.png") shouldBe null
        }

    @Test
    fun `createBitmapClip returns null for a file name that escapes the cache directory`() =
        runTest {
            ctx.createBitmapClip(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), "label", "../evil.png") shouldBe null
            File(ctx.cacheDir, "clipboard").listFiles()!!.shouldBeEmpty()
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
            val shareFile = act.createBitmapShareFile(bitmapWriting("share"), "test.png")
            act.shareBitmap(shareFile) shouldBe LaunchOutcome.Started
            val pendingUri = Uri.parse(act.startedChooserTarget().streamUri())

            ctx.createBitmapClip(bitmapWriting("clipboard"), "label", "test.png").shouldNotBeNull()

            pendingUri.readText() shouldBe "share"
        }

    @Test
    fun `createBitmapClip writes two clips with the same file name to files of their own`() =
        runTest {
            val first =
                ctx
                    .createBitmapClip(bitmapWriting("first"), "label", "test.png")
                    .shouldNotBeNull()
                    .getItemAt(0)
                    .uri
            val second =
                ctx
                    .createBitmapClip(bitmapWriting("second"), "label", "test.png")
                    .shouldNotBeNull()
                    .getItemAt(0)
                    .uri

            first shouldNotBe second
            listOf(first.readText(), second.readText()) shouldContainExactly listOf("first", "second")
        }

    @Test
    fun `createBitmapClip deletes clips older than the retention once it writes a new clip`() =
        runTest {
            ctx.expiredCacheWrite(CacheFileKind.CLIPBOARD, "expired")

            ctx.createBitmapClip(bitmapWriting("new"), "label", "test.png").shouldNotBeNull()

            ctx.cacheWriteFiles("clipboard").map { it.readText() } shouldContainExactly listOf("new")
        }

    @Test
    fun `createBitmapClip that cannot encode the bitmap keeps the file of an expired clip`() =
        runTest {
            val expired = ctx.expiredCacheWrite(CacheFileKind.CLIPBOARD, "expired")
            val bitmap = mockk<Bitmap>()
            every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false

            ctx.createBitmapClip(bitmap, "label", "test.png") shouldBe null

            ctx.cacheWriteFiles("clipboard").map { it.readText() } shouldContainExactly listOf("expired")
            expired.readText() shouldBe "expired"
        }

    @Test
    fun `createBitmapClip without a FileProvider for the package keeps the file of an expired clip`() =
        runTest {
            val expired = ctx.expiredCacheWrite(CacheFileKind.CLIPBOARD, "expired")

            contextWithoutFileProvider().createBitmapClip(bitmapWriting("new"), "label", "test.png") shouldBe null

            ctx.cacheWriteFiles("clipboard").map { it.readText() } shouldContainExactly listOf("expired")
            expired.readText() shouldBe "expired"
        }

    @Test
    fun `createBitmapClip whose encoding throws keeps the file of an expired clip`() =
        runTest {
            val expired = ctx.expiredCacheWrite(CacheFileKind.CLIPBOARD, "expired")
            val bitmap = mockk<Bitmap>()
            every { bitmap.compress(any(), any(), any<OutputStream>()) } throws IOException("disk full")

            ctx.createBitmapClip(bitmap, "label", "test.png") shouldBe null

            ctx.cacheWriteFiles("clipboard").map { it.readText() } shouldContainExactly listOf("expired")
            expired.readText() shouldBe "expired"
        }

    // ── createBitmapShareFile ───────────────────────────────────────────────────

    @Test
    fun `createBitmapShareFile returns the content uri of the written PNG`() =
        runTest {
            val uri = ctx.createBitmapShareFile(bitmapWriting("png"), "test.png").shouldBeInstanceOf<BitmapShareFile.Written>().uri

            uri.toString() shouldBe writtenUri("share", "test.png")
            uri.readText() shouldBe "png"
        }

    @Test
    fun `createBitmapShareFile writes on the given IO dispatcher`() =
        runTest {
            val io = HeldDispatcher()
            val file = async { ctx.createBitmapShareFile(bitmapWriting("png"), "test.png", io) }
            runCurrent()

            file.isCompleted.shouldBeFalse()
            ctx.cacheWriteFiles("share").shouldBeEmpty()

            io.held.removeFirst().run()
            file.await() shouldBe BitmapShareFile.Written(Uri.parse(writtenUri("share", "test.png")))
            ctx.cacheWriteFiles("share").map { it.readText() } shouldContainExactly listOf("png")
        }

    @Test
    fun `overlapping createBitmapShareFile writes with the same file name each keep their own bytes`() =
        runTest {
            val io = HeldDispatcher()
            val first = async { ctx.createBitmapShareFile(bitmapWriting("first"), "test.png", io) }
            val second = async { ctx.createBitmapShareFile(bitmapWriting("second"), "test.png", io) }
            runCurrent()

            io.held.removeLast().run()
            io.held.removeFirst().run()

            val firstUri = first.await().shouldBeInstanceOf<BitmapShareFile.Written>().uri
            val secondUri = second.await().shouldBeInstanceOf<BitmapShareFile.Written>().uri
            firstUri shouldNotBe secondUri
            listOf(firstUri.readText(), secondUri.readText()) shouldContainExactly listOf("first", "second")
        }

    @Test
    fun `createBitmapShareFile outside an input keeps a pending share's bytes`() =
        runTest {
            val act = activity()
            act.shareBitmap(act.createBitmapShareFile(bitmapWriting("pending"), "test.png")) shouldBe LaunchOutcome.Started
            val pendingUri = Uri.parse(act.startedChooserTarget().streamUri())

            val later = ctx.createBitmapShareFile(bitmapWriting("later"), "test.png").shouldBeInstanceOf<BitmapShareFile.Written>().uri

            pendingUri.readText() shouldBe "pending"
            later.readText() shouldBe "later"
        }

    @Test
    fun `createBitmapShareFile deletes writes older than the retention and keeps a pending share's file`() =
        runTest {
            val act = activity()
            act.shareBitmap(act.createBitmapShareFile(bitmapWriting("pending"), "test.png")) shouldBe LaunchOutcome.Started
            val pendingUri = Uri.parse(act.startedChooserTarget().streamUri())
            val expired = ctx.createCacheWriteDirectory(CacheFileKind.SHARE).root
            File(expired, "test.png").writeText("expired")
            expired.setLastModified(0).shouldBeTrue()

            val written = ctx.createBitmapShareFile(bitmapWriting("new"), "test.png").shouldBeInstanceOf<BitmapShareFile.Written>().uri

            ctx.cacheWriteFiles("share").map { it.readText() } shouldContainExactlyInAnyOrder listOf("pending", "new")
            pendingUri.readText() shouldBe "pending"
            written.readText() shouldBe "new"
        }

    @Test
    fun `createBitmapShareFile that returns Failed keeps the file of an expired share`() =
        runTest {
            val expired = ctx.expiredCacheWrite(CacheFileKind.SHARE, "expired")
            val bitmap = mockk<Bitmap>()
            every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false

            ctx.createBitmapShareFile(bitmap, "test.png") shouldBe BitmapShareFile.Failed

            ctx.cacheWriteFiles("share").map { it.readText() } shouldContainExactly listOf("expired")
            expired.readText() shouldBe "expired"
        }

    @Test
    fun `createBitmapShareFile whose encoding throws keeps the file of an expired share`() =
        runTest {
            val expired = ctx.expiredCacheWrite(CacheFileKind.SHARE, "expired")
            val bitmap = mockk<Bitmap>()
            every { bitmap.compress(any(), any(), any<OutputStream>()) } throws IOException("disk full")

            ctx.createBitmapShareFile(bitmap, "test.png") shouldBe BitmapShareFile.Failed

            ctx.cacheWriteFiles("share").map { it.readText() } shouldContainExactly listOf("expired")
            expired.readText() shouldBe "expired"
        }

    @Test
    fun `createBitmapShareFile without a FileProvider for the package keeps the file of an expired share`() =
        runTest {
            val expired = ctx.expiredCacheWrite(CacheFileKind.SHARE, "expired")

            contextWithoutFileProvider().createBitmapShareFile(bitmapWriting("new"), "test.png") shouldBe BitmapShareFile.Failed

            ctx.cacheWriteFiles("share").map { it.readText() } shouldContainExactly listOf("expired")
            expired.readText() shouldBe "expired"
        }

    @Test
    fun `createBitmapShareFile returns Failed and deletes the cache file when the bitmap cannot be encoded`() =
        runTest {
            val bitmap = mockk<Bitmap>()
            every { bitmap.compress(any(), any(), any<OutputStream>()) } returns false

            ctx.createBitmapShareFile(bitmap, "test.png") shouldBe BitmapShareFile.Failed
            File(ctx.cacheDir, "share").listFiles()!!.shouldBeEmpty()
        }

    @Test
    fun `createBitmapShareFile returns Failed and deletes the written file without a FileProvider for the package`() =
        runTest {
            contextWithoutFileProvider().createBitmapShareFile(bitmapWriting("png"), "test.png") shouldBe BitmapShareFile.Failed

            File(ctx.cacheDir, "share").listFiles()!!.shouldBeEmpty()
        }

    @Test
    fun `createBitmapShareFile returns Failed when the share cache directory cannot be created`() =
        runTest {
            blockCacheDirectory("share")

            ctx.createBitmapShareFile(bitmapWriting("png"), "test.png") shouldBe BitmapShareFile.Failed
        }

    @Test
    fun `createBitmapShareFile returns Failed for a file name that escapes the cache directory`() =
        runTest {
            ctx.createBitmapShareFile(bitmapWriting("png"), "../evil.png") shouldBe BitmapShareFile.Failed
            File(ctx.cacheDir, "share").listFiles()!!.shouldBeEmpty()
        }

    // ── shareBitmap ─────────────────────────────────────────────────────────────

    @Test
    fun `shareBitmap sends the content uri with read permission through a chooser`() {
        val act = activity()

        act.shareBitmap(BitmapShareFile.Written(Uri.parse("$CACHE_ROOT_URI/share/test.png"))) shouldBe LaunchOutcome.Started

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

        act.shareBitmap(BitmapShareFile.Written(Uri.parse("$CACHE_ROOT_URI/share/test.png")), "optional text") shouldBe
            LaunchOutcome.Started

        act.startedChooserTarget().getStringExtra(Intent.EXTRA_TEXT) shouldBe "optional text"
    }

    @Test
    fun `shareBitmap with a failed file shows the share toast and starts nothing`() {
        val act = activity()

        act.shareBitmap(BitmapShareFile.Failed) shouldBe LaunchOutcome.Failed

        latestToastIsShareNotSupported()
        shadowOf(act).nextStartedActivity shouldBe null
    }

    @Test
    fun `shareBitmap with a dropped file starts nothing and shows no toast`() {
        val act = activity()

        act.shareBitmap(BitmapShareFile.Dropped) shouldBe LaunchOutcome.Dropped

        ShadowToast.shownToastCount() shouldBe 0
        shadowOf(act).nextStartedActivity shouldBe null
    }

    @Test
    fun `shareBitmap SecurityException from startActivity shows the share toast and returns Failed`() {
        val failing = StartActivityFailingContext(ctx, SecurityException("chooser denied"))

        failing.shareBitmap(BitmapShareFile.Written(Uri.parse("$CACHE_ROOT_URI/share/test.png"))) shouldBe LaunchOutcome.Failed

        failing.startedIntents.single().action shouldBe Intent.ACTION_CHOOSER
        latestToastIsShareNotSupported()
    }

    // ── quickShareBitmap ────────────────────────────────────────────────────────

    @Test
    fun `quickShareBitmap without Quick Share sends the content uri with read permission`() {
        val act = activity()

        act.quickShareBitmap(BitmapShareFile.Written(Uri.parse("$CACHE_ROOT_URI/share/test.png"))) shouldBe LaunchOutcome.Started

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

        act.quickShareBitmap(BitmapShareFile.Written(Uri.parse("$CACHE_ROOT_URI/share/test.png"))) shouldBe LaunchOutcome.Started

        val intent = shadowOf(act).nextStartedActivity.shouldNotBeNull()
        intent.`package` shouldBe "com.samsung.android.app.sharelive"
        intent.streamUri() shouldBe "$CACHE_ROOT_URI/share/test.png"
    }

    @Test
    fun `quickShareBitmap with a failed file shows the share toast and starts nothing`() {
        val act = activity()

        act.quickShareBitmap(BitmapShareFile.Failed) shouldBe LaunchOutcome.Failed

        latestToastIsShareNotSupported()
        shadowOf(act).nextStartedActivity shouldBe null
    }

    @Test
    fun `quickShareBitmap with a dropped file starts nothing and shows no toast`() {
        val act = activity()

        act.quickShareBitmap(BitmapShareFile.Dropped) shouldBe LaunchOutcome.Dropped

        ShadowToast.shownToastCount() shouldBe 0
        shadowOf(act).nextStartedActivity shouldBe null
    }

    @Test
    fun `quickShareBitmap SecurityException from the explicit Quick Share start shows the share toast and returns Failed`() {
        installQuickShare()
        val failing = StartActivityFailingContext(ctx, SecurityException("Quick Share activity not exported"))

        failing.quickShareBitmap(BitmapShareFile.Written(Uri.parse("$CACHE_ROOT_URI/share/test.png"))) shouldBe LaunchOutcome.Failed

        failing.startedIntents.single().`package` shouldBe "com.samsung.android.app.sharelive"
        latestToastIsShareNotSupported()
    }

    @Test
    fun `quickShareBitmap retries without the package and toasts when no activity handles either intent`() {
        installQuickShare()
        val failing = StartActivityFailingContext(ctx, ActivityNotFoundException("no handler"))

        failing.quickShareBitmap(BitmapShareFile.Written(Uri.parse("$CACHE_ROOT_URI/share/test.png"))) shouldBe LaunchOutcome.Failed

        failing.startedIntents.map { it.`package` } shouldContainExactly listOf("com.samsung.android.app.sharelive", null)
        latestToastIsShareNotSupported()
    }

    // ── File / List<File>.share ──────────────────────────────────────────────────

    @Test
    fun `single File share - sends the file's content uri with read permission and returns Started`() {
        val act = activity()
        val file = File(act.cacheDir, "img.png").also { it.createNewFile() }
        file.share(act) shouldBe LaunchOutcome.Started
        val target = act.startedChooserTarget()
        target.streamUri() shouldBe "$CACHE_ROOT_URI/img.png"
        target.readGrantFlag() shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    @Test
    fun `single-element List share - uses ACTION_SEND and returns Started`() {
        val act = activity()
        val file = File(act.cacheDir, "img.png").also { it.createNewFile() }
        listOf(file).share(act) shouldBe LaunchOutcome.Started
        act.startedChooserTarget().action shouldBe Intent.ACTION_SEND
    }

    @Test
    fun `multi-element List share - sends every content uri with ACTION_SEND_MULTIPLE and returns Started`() {
        val act = activity()
        val f1 = File(act.cacheDir, "img1.png").also { it.createNewFile() }
        val f2 = File(act.cacheDir, "img2.png").also { it.createNewFile() }
        listOf(f1, f2).share(act) shouldBe LaunchOutcome.Started
        val target = act.startedChooserTarget()
        target.action shouldBe Intent.ACTION_SEND_MULTIPLE
        IntentCompat.getParcelableArrayListExtra(target, Intent.EXTRA_STREAM, Uri::class.java)?.map(Uri::toString) shouldBe
            listOf("$CACHE_ROOT_URI/img1.png", "$CACHE_ROOT_URI/img2.png")
        target.readGrantFlag() shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    @Test
    fun `List share of a file outside every configured root - exception caught, returns Failed`() {
        val act = activity()
        val outside = File(act.dataDir, "outside.png").also { it.createNewFile() }
        outside.share(act) shouldBe LaunchOutcome.Failed
        shadowOf(act).nextStartedActivity shouldBe null
        latestToastIsShareNotSupported()
    }

    @Test
    fun `List share SecurityException from startActivity shows the share toast and returns Failed`() {
        val failing = StartActivityFailingContext(ctx, SecurityException("chooser denied"))
        val file = File(ctx.cacheDir, "img.png").also { it.createNewFile() }
        listOf(file).share(failing) shouldBe LaunchOutcome.Failed
        failing.startedIntents.single().action shouldBe Intent.ACTION_CHOOSER
        latestToastIsShareNotSupported()
    }

    @Test
    fun `File share ActivityNotFoundException returns Failed`() {
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
        file.share(a) shouldBe LaunchOutcome.Failed
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
        file.share(a) shouldBe LaunchOutcome.Started
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
    fun `Fragment createBitmapShareFile writes through the fragment's context`() =
        runTest {
            attachedFragment().createBitmapShareFile(bitmapWriting("png"), "test.png") shouldBe
                BitmapShareFile.Written(Uri.parse(writtenUri("share", "test.png")))
        }

    @Test
    fun `Fragment shareBitmap starts the chooser from the fragment's activity`() {
        val fragment = attachedFragment()

        fragment.shareBitmap(BitmapShareFile.Written(Uri.parse("$CACHE_ROOT_URI/share/test.png"))) shouldBe LaunchOutcome.Started

        fragment.requireActivity().startedChooserTarget().streamUri() shouldBe "$CACHE_ROOT_URI/share/test.png"
    }

    @Test
    fun `Fragment quickShareBitmap starts the share from the fragment's activity`() {
        val fragment = attachedFragment()

        fragment.quickShareBitmap(BitmapShareFile.Written(Uri.parse("$CACHE_ROOT_URI/share/test.png"))) shouldBe LaunchOutcome.Started

        shadowOf(fragment.requireActivity()).nextStartedActivity.shouldNotBeNull().streamUri() shouldBe
            "$CACHE_ROOT_URI/share/test.png"
    }

    @Test
    fun `Fragment shareText delegates to Context shareText`() {
        attachedFragment().shareText("hello from fragment", "fragment title") shouldBe LaunchOutcome.Started
    }

    @Test
    fun `Fragment shareText without title covers default-param synthetic`() {
        attachedFragment().shareText("hello from fragment") shouldBe LaunchOutcome.Started
    }

    @Test
    fun `Fragment shareApp delegates to Context shareApp`() {
        attachedFragment().shareApp() shouldBe LaunchOutcome.Started
    }
}

@Implements(ClipboardManager::class)
private class ShadowDeniedClipboardManager : ShadowClipboardManager() {
    @Implementation
    override fun setPrimaryClip(clip: ClipData): Unit = throw SecurityException("clipboard access denied")
}
