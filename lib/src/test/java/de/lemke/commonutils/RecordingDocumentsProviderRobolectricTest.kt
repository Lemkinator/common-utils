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
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecordingDocumentsProviderRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `deleting the document records its URI and removes the file`() {
        val provider = RecordingDocumentsProvider.create(ctx)
        provider.file.exists() shouldBe true

        DocumentsContract.deleteDocument(ctx.contentResolver, provider.uri) shouldBe true

        provider.deleted shouldContainExactly listOf(provider.uri)
        provider.file.exists() shouldBe false
    }

    @Test
    fun `a failing delete still records the URI and keeps the file`() {
        val provider = RecordingDocumentsProvider.create(ctx).apply { deleteFailure = IllegalStateException("denied") }

        shouldThrow<IllegalStateException> { DocumentsContract.deleteDocument(ctx.contentResolver, provider.uri) }

        provider.deleted shouldContainExactly listOf(provider.uri)
        provider.file.exists() shouldBe true
    }
}
