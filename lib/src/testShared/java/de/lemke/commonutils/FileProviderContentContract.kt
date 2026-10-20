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
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

internal const val TEST_AUTHORITY = "de.lemke.commonutils.test.fileprovider"

/** The behavior of the stock [FileProvider] that [ShadowFileProvider] reproduces, run on the JVM and on a device. */
abstract class FileProviderContentContract {
    protected abstract val resolvesOnThisPlatform: Boolean

    protected val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun assumeResolvesOnThisPlatform() = assumeTrue(resolvesOnThisPlatform)

    protected fun cacheUri(name: String): Uri = FileProvider.getUriForFile(ctx, TEST_AUTHORITY, File(ctx.cacheDir, name))

    private fun writeThroughDescriptor(
        uri: Uri,
        mode: String,
        bytes: ByteArray,
    ) = ParcelFileDescriptor
        .AutoCloseOutputStream(ctx.contentResolver.openFileDescriptor(uri, mode).shouldNotBeNull())
        .use { it.write(bytes) }

    @Test
    fun openOutputStreamThenOpenInputStreamRoundTripsTheBytes() {
        val uri = cacheUri("roundtrip.bin")

        ctx.contentResolver
            .openOutputStream(uri)
            .shouldNotBeNull()
            .use { it.write("123".toByteArray()) }

        File(ctx.cacheDir, "roundtrip.bin").readBytes() shouldBe "123".toByteArray()
        ctx.contentResolver
            .openInputStream(uri)
            .shouldNotBeNull()
            .use { it.readBytes() } shouldBe "123".toByteArray()
    }

    @Test
    fun openOutputStreamTruncatesTheExistingFile() {
        val file = File(ctx.cacheDir, "truncate.bin").apply { writeBytes("12345".toByteArray()) }

        ctx.contentResolver
            .openOutputStream(cacheUri("truncate.bin"))
            .shouldNotBeNull()
            .use { it.write("98".toByteArray()) }

        file.readBytes() shouldBe "98".toByteArray()
    }

    @Test
    fun openOutputStreamInWtModeTruncatesTheExistingFile() {
        val file = File(ctx.cacheDir, "truncate.bin").apply { writeBytes("12345".toByteArray()) }

        ctx.contentResolver
            .openOutputStream(cacheUri("truncate.bin"), "wt")
            .shouldNotBeNull()
            .use { it.write("98".toByteArray()) }

        file.readBytes() shouldBe "98".toByteArray()
    }

    @Test
    fun openFileDescriptorInRwtModeTruncatesTheExistingFile() {
        val file = File(ctx.cacheDir, "truncate.bin").apply { writeBytes("12345".toByteArray()) }

        writeThroughDescriptor(cacheUri("truncate.bin"), "rwt", "98".toByteArray())

        file.readBytes() shouldBe "98".toByteArray()
    }

    @Test
    fun openOutputStreamInWaModeAppendsToTheExistingFile() {
        val file = File(ctx.cacheDir, "append.bin").apply { writeBytes("123".toByteArray()) }

        ctx.contentResolver
            .openOutputStream(cacheUri("append.bin"), "wa")
            .shouldNotBeNull()
            .use { it.write("45".toByteArray()) }

        file.readBytes() shouldBe "12345".toByteArray()
    }

    @Test
    fun openFileDescriptorInRwModeOverwritesInPlaceWithoutTruncating() {
        val file = File(ctx.cacheDir, "overwrite.bin").apply { writeBytes("12345".toByteArray()) }

        writeThroughDescriptor(cacheUri("overwrite.bin"), "rw", "98".toByteArray())

        file.readBytes() shouldBe "98345".toByteArray()
    }

    @Test
    fun openFileDescriptorInRwModeCreatesAMissingFile() {
        val file = File(ctx.cacheDir, "created.bin")
        file.exists().shouldBeFalse()

        writeThroughDescriptor(cacheUri("created.bin"), "rw", "7".toByteArray())

        file.readBytes() shouldBe "7".toByteArray()
    }

    @Test
    fun openInputStreamReadsAFileUnderANestedRoot() {
        val file = File(File(ctx.filesDir, "nested/dir"), "doc.txt")
        file.parentFile.shouldNotBeNull().mkdirs()
        file.writeText("nested")
        val uri = FileProvider.getUriForFile(ctx, TEST_AUTHORITY, file)

        ctx.contentResolver
            .openInputStream(uri)
            .shouldNotBeNull()
            .use { it.readBytes().decodeToString() } shouldBe "nested"
    }

    @Test
    fun openFileDescriptorWithAnUnknownModeThrowsIllegalArgumentException() {
        val e = shouldThrow<IllegalArgumentException> { ctx.contentResolver.openFileDescriptor(cacheUri("photo.png"), "x") }
        e.message shouldBe "Invalid mode: x"
    }

    @Test
    fun getTypeDerivesTheMimeTypeFromTheFileExtension() {
        ctx.contentResolver.getType(cacheUri("photo.png")) shouldBe "image/png"
    }

    @Test
    fun getTypeFallsBackToOctetStreamWithoutAnExtension() {
        ctx.contentResolver.getType(cacheUri("README")) shouldBe "application/octet-stream"
    }

    @Test
    fun queryReportsTheDisplayNameAndSizeOfTheFile() {
        File(ctx.cacheDir, "photo.png").writeBytes("12345".toByteArray())

        ctx.contentResolver.query(cacheUri("photo.png"), null, null, null, null).shouldNotBeNull().use { cursor ->
            cursor.columnNames.toList() shouldBe listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            cursor.moveToFirst().shouldBeTrue()
            cursor.getString(0) shouldBe "photo.png"
            cursor.getLong(1) shouldBe "12345".length.toLong()
        }
    }

    @Test
    fun queryReportsTheDisplayNameParameterAndDropsUnknownProjectionColumns() {
        val file = File(ctx.cacheDir, "photo.png")
        val uri = FileProvider.getUriForFile(ctx, TEST_AUTHORITY, file, "shared.png")

        ctx.contentResolver.query(uri, arrayOf("_data", OpenableColumns.DISPLAY_NAME), null, null, null).shouldNotBeNull().use { cursor ->
            cursor.columnNames.toList() shouldBe listOf(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst().shouldBeTrue()
            cursor.getString(0) shouldBe "shared.png"
        }
    }
}
