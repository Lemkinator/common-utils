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
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.CacheFileKind
import de.lemke.commonutils.ui.utils.createCacheWriteDirectory
import de.lemke.commonutils.ui.utils.getFileUri
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotMatch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowFileProvider::class])
class CacheWriteUrisRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `cacheWriteUriPattern matches the URI of a file in a cache write of that kind`() {
        val file = ctx.createCacheWriteDirectory(CacheFileKind.SHARE).resolve("icon.png").apply { writeText("x") }

        file.getFileUri(ctx).toString() shouldMatch ctx.cacheWriteUriPattern("cache_root", "share", "icon.png")
    }

    @Test
    fun `cacheWriteUriPattern rejects another kind, file name and path name`() {
        val uri =
            ctx
                .createCacheWriteDirectory(CacheFileKind.SHARE)
                .resolve("icon.png")
                .apply { writeText("x") }
                .getFileUri(ctx)
                .toString()

        uri shouldNotMatch ctx.cacheWriteUriPattern("cache_root", "clipboard", "icon.png")
        uri shouldNotMatch ctx.cacheWriteUriPattern("cache_root", "share", "other.png")
        uri shouldNotMatch ctx.cacheWriteUriPattern("elsewhere", "share", "icon.png")
    }

    @Test
    fun `cacheFileOf resolves the URI of a cache write to its file`() {
        val file = ctx.createCacheWriteDirectory(CacheFileKind.CLIPBOARD).resolve("icon.png").apply { writeText("x") }

        ctx.cacheFileOf(file.getFileUri(ctx)).canonicalFile shouldBe file.canonicalFile
    }
}
