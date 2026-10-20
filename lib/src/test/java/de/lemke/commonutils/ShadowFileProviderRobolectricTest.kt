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
import android.content.ContextWrapper
import android.content.res.XmlResourceParser
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.test.R
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.io.File
import java.io.IOException
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
        val parser = mockk<XmlResourceParser> { every { next() } throws parseError }

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
        val parser = mockk<XmlResourceParser> { every { next() } throws readError }

        val e =
            shouldThrow<IllegalArgumentException> {
                FileProvider.getUriForFile(contextWithPathsParser(parser), TEST_AUTHORITY, File(ctx.cacheDir, "photo.png"))
            }

        e.message shouldBe "Failed to parse android.support.FILE_PROVIDER_PATHS meta-data"
        e.cause shouldBeSameInstanceAs readError
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
}

/** Runs on every OS: the parity class above skips where the stock provider cannot resolve paths. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResetFileProviderCacheRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun cacheSize(): Int {
        val sCache =
            FileProvider::class.java
                .getDeclaredField("sCache")
                .apply { isAccessible = true }
                .get(null) as Map<*, *>
        return synchronized(sCache) { sCache.size }
    }

    private fun lookUpCacheFile() {
        // The strategy is cached before the root check; on Windows the check then throws.
        runCatching { FileProvider.getUriForFile(ctx, TEST_AUTHORITY, File(ctx.cacheDir, "photo.png")) }
    }

    @Test
    fun `reset empties the stock path-strategy cache and a new lookup fills it again`() {
        resetFileProviderCache()
        lookUpCacheFile()
        cacheSize() shouldBe 1

        resetFileProviderCache()
        cacheSize() shouldBe 0

        lookUpCacheFile()
        cacheSize() shouldBe 1
        resetFileProviderCache()
    }
}
