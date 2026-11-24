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

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.PackageManager.DONT_KILL_APP
import android.content.res.XmlResourceParser
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.test.R
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import java.io.File
import java.io.IOException
import org.junit.After
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParserException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowFileProvider::class])
class ShadowFileProviderRobolectricTest : FileProviderContentContract() {
    override val resolvesOnThisPlatform = true

    @Test
    fun `cache-path root maps a cache file to its uri`() {
        val file = File(ctx.cacheDir, "photo.png")
        val uri = FileProvider.getUriForFile(ctx, TEST_AUTHORITY, file)
        uri.toString() shouldBe "content://$TEST_AUTHORITY/cache_root/photo.png"
    }

    @Test
    fun `file name is percent-encoded in the uri path`() {
        val file = File(ctx.cacheDir, "my photo.png")
        val uri = FileProvider.getUriForFile(ctx, TEST_AUTHORITY, file)
        uri.toString() shouldBe "content://$TEST_AUTHORITY/cache_root/my%20photo.png"
    }

    @Test
    fun `files-path root with nested path attribute maps a nested file`() {
        val file = File(File(ctx.filesDir, "nested/dir"), "doc.txt")
        val uri = FileProvider.getUriForFile(ctx, TEST_AUTHORITY, file)
        uri.toString() shouldBe "content://$TEST_AUTHORITY/nested_files/doc.txt"
    }

    @Test
    fun `longest matching root wins when roots overlap`() {
        val nested = File(File(ctx.filesDir, "nested/dir"), "inner.txt")
        val outer = File(ctx.filesDir, "outer.txt")

        FileProvider.getUriForFile(ctx, TEST_AUTHORITY, nested).encodedPath!! shouldStartWith "/nested_files/"
        FileProvider.getUriForFile(ctx, TEST_AUTHORITY, outer).encodedPath!! shouldStartWith "/files_root/"
    }

    @Test
    fun `roots come from each call's context, not from an earlier call for the same authority`() {
        val otherCacheDir = File(ctx.dataDir, "other-cache")
        val otherCtx =
            object : ContextWrapper(ctx) {
                override fun getCacheDir() = otherCacheDir
            }

        FileProvider.getUriForFile(otherCtx, TEST_AUTHORITY, File(otherCacheDir, "first.png")).toString() shouldBe
            "content://$TEST_AUTHORITY/cache_root/first.png"
        FileProvider.getUriForFile(ctx, TEST_AUTHORITY, File(ctx.cacheDir, "second.png")).toString() shouldBe
            "content://$TEST_AUTHORITY/cache_root/second.png"
    }

    @Test
    fun `file outside every configured root throws IllegalArgumentException`() {
        val outside = File(System.getProperty("java.io.tmpdir"), "shadow-file-provider-outside.bin")
        shouldThrow<IllegalArgumentException> {
            FileProvider.getUriForFile(ctx, TEST_AUTHORITY, outside)
        }
    }

    @Test
    fun `file without a canonical path throws IllegalArgumentException wrapping the IOException`() {
        val invalid = File(ctx.cacheDir, "bad\u0000.png")

        val e = shouldThrow<IllegalArgumentException> { FileProvider.getUriForFile(ctx, TEST_AUTHORITY, invalid) }

        e.message shouldBe "Failed to resolve canonical path for ${ctx.cacheDir}${File.separator}bad\u0000.png"
        e.cause.shouldBeInstanceOf<IOException>().message shouldBe "Invalid file path"
    }

    private fun contextWithPathsParser(parser: XmlResourceParser): Context {
        val packageManager = spyk(ctx.packageManager)
        every { packageManager.getXml(ctx.packageName, R.xml.test_file_provider_paths, any()) } returns parser
        return object : ContextWrapper(ctx) {
            override fun getPackageManager() = packageManager
        }
    }

    @Test
    fun `malformed paths meta-data throws IllegalArgumentException wrapping the XmlPullParserException`() {
        val parseError = XmlPullParserException("unexpected end of document")
        val parser =
            mockk<XmlResourceParser> {
                every { next() } throws parseError
                every { close() } just Runs
            }

        val e =
            shouldThrow<IllegalArgumentException> {
                FileProvider.getUriForFile(contextWithPathsParser(parser), TEST_AUTHORITY, File(ctx.cacheDir, "photo.png"))
            }

        e.message shouldBe "Failed to parse android.support.FILE_PROVIDER_PATHS meta-data"
        e.cause shouldBeSameInstanceAs parseError
    }

    @Test
    fun `unreadable paths meta-data throws IllegalArgumentException wrapping the IOException`() {
        val readError = IOException("resource stream closed")
        val parser =
            mockk<XmlResourceParser> {
                every { next() } throws readError
                every { close() } just Runs
            }

        val e =
            shouldThrow<IllegalArgumentException> {
                FileProvider.getUriForFile(contextWithPathsParser(parser), TEST_AUTHORITY, File(ctx.cacheDir, "photo.png"))
            }

        e.message shouldBe "Failed to parse android.support.FILE_PROVIDER_PATHS meta-data"
        e.cause shouldBeSameInstanceAs readError
    }

    @Test
    fun `root-path maps a file under the device root`() {
        ctx.declarePaths(R.xml.test_file_provider_paths_all_roots)

        FileProvider.getUriForFile(ctx, TEST_AUTHORITY, File("/device.txt")).toString() shouldBe
            "content://$TEST_AUTHORITY/device_root/device.txt"
    }

    @Test
    fun `external-path maps a file under the external storage directory`() {
        ctx.declarePaths(R.xml.test_file_provider_paths_all_roots)

        FileProvider.getUriForFile(ctx, TEST_AUTHORITY, ctx.fileInExternalStorage()).toString() shouldBe
            "content://$TEST_AUTHORITY/external_root/external.txt"
    }

    @Test
    fun `external-files-path maps a file under the first external files directory`() {
        ctx.declarePaths(R.xml.test_file_provider_paths_all_roots)

        FileProvider.getUriForFile(ctx, TEST_AUTHORITY, ctx.fileInExternalFiles()).toString() shouldBe
            "content://$TEST_AUTHORITY/external_files_root/external-files.txt"
    }

    @Test
    fun `external-cache-path maps a file under the first external cache directory`() {
        ctx.declarePaths(R.xml.test_file_provider_paths_all_roots)

        FileProvider.getUriForFile(ctx, TEST_AUTHORITY, ctx.fileInExternalCache()).toString() shouldBe
            "content://$TEST_AUTHORITY/external_cache_root/external-cache.txt"
    }

    @Test
    fun `external-media-path maps a file under the first external media directory`() {
        ctx.declarePaths(R.xml.test_file_provider_paths_all_roots)

        FileProvider.getUriForFile(ctx.withExternalMediaDir(), TEST_AUTHORITY, ctx.fileInExternalMedia()).toString() shouldBe
            "content://$TEST_AUTHORITY/external_media_root/external-media.txt"
    }

    @Test
    fun `an empty name attribute throws IllegalArgumentException like stock`() {
        ctx.declarePaths(R.xml.test_file_provider_paths_empty_name)

        val e = shouldThrow<IllegalArgumentException> { cacheUri("photo.png") }

        e.message shouldBe "Name must not be empty"
    }

    @Test
    fun `an authority without a provider throws IllegalArgumentException like stock`() {
        val e =
            shouldThrow<IllegalArgumentException> {
                FileProvider.getUriForFile(ctx, "de.lemke.unknown", File(ctx.cacheDir, "photo.png"))
            }

        e.message shouldBe "Couldn't find meta-data for provider with authority de.lemke.unknown"
    }

    @Test
    fun `a provider without paths meta-data throws IllegalArgumentException like stock`() {
        ctx.declarePaths(null)

        val e = shouldThrow<IllegalArgumentException> { cacheUri("photo.png") }

        e.message shouldBe "Missing android.support.FILE_PROVIDER_PATHS meta-data"
    }

    @Test
    fun `displayName overload appends the query parameter without changing the path`() {
        val file = File(ctx.cacheDir, "photo.png")
        val plain = FileProvider.getUriForFile(ctx, TEST_AUTHORITY, file)
        val withDisplayName = FileProvider.getUriForFile(ctx, TEST_AUTHORITY, file, "shared.png")

        withDisplayName.encodedPath shouldBe plain.encodedPath
        withDisplayName.getQueryParameter("displayName") shouldBe "shared.png"
    }

    @Test
    fun `delete removes the file and reports one deleted row`() {
        val file = File(ctx.cacheDir, "photo.png").apply { writeBytes(ByteArray(1)) }

        ctx.contentResolver.delete(cacheUri("photo.png"), null, null) shouldBe 1
        file.exists().shouldBeFalse()
    }

    @Test
    fun `delete of a missing file reports zero deleted rows`() {
        ctx.contentResolver.delete(cacheUri("missing.png"), null, null) shouldBe 0
    }

    @Test
    fun `uri that escapes its root throws SecurityException like stock`() {
        val escaping = Uri.parse("content://$TEST_AUTHORITY/cache_root/..%2F..%2Foutside.bin")

        val e = shouldThrow<SecurityException> { ctx.contentResolver.delete(escaping, null, null) }
        e.message shouldBe "Resolved path jumped beyond configured root"
    }

    @Test
    fun `uri with an unconfigured root name throws IllegalArgumentException like stock`() {
        val unknownRoot = Uri.parse("content://$TEST_AUTHORITY/unknown_root/photo.png")

        val e = shouldThrow<IllegalArgumentException> { ctx.contentResolver.delete(unknownRoot, null, null) }
        e.message shouldBe "Unable to find configured root for $unknownRoot"
    }

    @Test
    fun `uri naming only a root throws IllegalArgumentException like stock`() {
        val rootOnly = Uri.parse("content://$TEST_AUTHORITY/cache_root")

        val e = shouldThrow<IllegalArgumentException> { ctx.contentResolver.delete(rootOnly, null, null) }
        e.message shouldBe "Unable to find path from root: $rootOnly"
    }
}

/** No [ShadowFileProvider] here: exercises the real, unshadowed [FileProvider] for comparison. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShadowFileProviderParityRobolectricTest : FileProviderContentContract() {
    override val resolvesOnThisPlatform = File.separatorChar == '/'

    @Test
    fun `shadow produces the same uri as the real FileProvider on posix separators`() {
        val file = File(File(ctx.filesDir, "nested/dir"), "doc.txt")
        val real = FileProvider.getUriForFile(ctx, TEST_AUTHORITY, file)
        val shadowed = ShadowFileProvider.getUriForFile(ctx, TEST_AUTHORITY, file)

        shadowed shouldBe real
    }

    @Test
    fun `shadow produces the same uri as the real FileProvider for every root tag`() {
        ctx.declarePaths(R.xml.test_file_provider_paths_all_roots)
        val context = ctx.withExternalMediaDir()

        listOf(
            File("/device.txt"),
            ctx.fileInExternalStorage(),
            ctx.fileInExternalFiles(),
            ctx.fileInExternalCache(),
            ctx.fileInExternalMedia(),
        ).forEach { file ->
            val real = FileProvider.getUriForFile(context, TEST_AUTHORITY, file)
            ShadowFileProvider.getUriForFile(context, TEST_AUTHORITY, file) shouldBe real
        }
    }

    @Test
    fun `shadow throws the same message as the real FileProvider for an empty name`() {
        ctx.declarePaths(R.xml.test_file_provider_paths_empty_name)
        val file = File(ctx.filesDir, "photo.png")

        val real = shouldThrow<IllegalArgumentException> { FileProvider.getUriForFile(ctx, TEST_AUTHORITY, file) }
        val shadowed = shouldThrow<IllegalArgumentException> { ShadowFileProvider.getUriForFile(ctx, TEST_AUTHORITY, file) }

        shadowed.message shouldBe real.message
        real.message shouldBe "Name must not be empty"
    }

    @Test
    fun `shadow throws the same message as the real FileProvider for an authority without a provider`() {
        val file = File(ctx.cacheDir, "photo.png")

        val real = shouldThrow<IllegalArgumentException> { FileProvider.getUriForFile(ctx, "de.lemke.unknown", file) }
        val shadowed = shouldThrow<IllegalArgumentException> { ShadowFileProvider.getUriForFile(ctx, "de.lemke.unknown", file) }

        shadowed.message shouldBe real.message
        real.message shouldBe "Couldn't find meta-data for provider with authority de.lemke.unknown"
    }

    @Test
    fun `shadow throws the same message as the real FileProvider for missing paths meta-data`() {
        ctx.declarePaths(null)
        val file = File(ctx.cacheDir, "photo.png")

        val real = shouldThrow<IllegalArgumentException> { FileProvider.getUriForFile(ctx, TEST_AUTHORITY, file) }
        val shadowed = shouldThrow<IllegalArgumentException> { ShadowFileProvider.getUriForFile(ctx, TEST_AUTHORITY, file) }

        shadowed.message shouldBe real.message
        real.message shouldBe "Missing android.support.FILE_PROVIDER_PATHS meta-data"
    }
}

private fun Context.declarePaths(resource: Int?) {
    val provider = packageManager.resolveContentProvider(TEST_AUTHORITY, PackageManager.GET_META_DATA)!!
    if (resource == null) provider.metaData = null else provider.metaData.putInt("android.support.FILE_PROVIDER_PATHS", resource)
    shadowOf(packageManager).addOrUpdateProvider(provider)
}

private fun Context.fileInExternalStorage(): File {
    @Suppress("DEPRECATION")
    val storage = Environment.getExternalStorageDirectory()
    return File(storage, "external.txt")
}

private fun Context.fileInExternalFiles(): File = File(getExternalFilesDirs(null).first(), "external-files.txt")

private fun Context.fileInExternalCache(): File = File(externalCacheDirs.first(), "external-cache.txt")

private fun Context.fileInExternalMedia(): File = File(dataDir, "media/external-media.txt")

private fun Context.withExternalMediaDir(): Context =
    object : ContextWrapper(this) {
        @Suppress("DEPRECATION")
        override fun getExternalMediaDirs(): Array<File> = arrayOf(File(dataDir, "media"))
    }

/** Exercises the reset on the stock provider; it needs POSIX separators to resolve roots, like the parity class above. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResetFileProviderCacheRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun lookUpCacheFile() = FileProvider.getUriForFile(ctx, TEST_AUTHORITY, File(ctx.cacheDir, "photo.png"))

    @After
    fun resetCache() {
        resetFileProviderCache()
    }

    @Test
    fun `reset makes the stock provider read its declared roots again`() {
        Assume.assumeTrue(File.separatorChar == '/')
        lookUpCacheFile().toString() shouldBe "content://$TEST_AUTHORITY/cache_root/photo.png"
        ctx.declarePaths(R.xml.test_file_provider_paths_files_only)
        lookUpCacheFile().toString() shouldBe "content://$TEST_AUTHORITY/cache_root/photo.png"

        resetFileProviderCache()

        val e = shouldThrow<IllegalArgumentException> { lookUpCacheFile() }
        e.message.shouldNotBeNull() shouldStartWith "Failed to find configured root that contains"
    }

    @Test
    fun `reset clears the cached root of a provider disabled in the meantime`() {
        Assume.assumeTrue(File.separatorChar == '/')
        lookUpCacheFile().toString() shouldBe "content://$TEST_AUTHORITY/cache_root/photo.png"
        ctx.declarePaths(R.xml.test_file_provider_paths_files_only)
        val component = ComponentName(ctx.packageName, FileProvider::class.java.name)
        ctx.packageManager.setComponentEnabledSetting(component, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)

        resetFileProviderCache()

        ctx.packageManager.setComponentEnabledSetting(component, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP)
        val e = shouldThrow<IllegalArgumentException> { lookUpCacheFile() }
        e.message.shouldNotBeNull() shouldStartWith "Failed to find configured root that contains"
    }
}
