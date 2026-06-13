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
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class ExportUtilsRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val bitmap: Bitmap get() = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    @Test
    fun `saveBitmapToUri returns false when uri is null`() {
        ctx.saveBitmapToUri(null, bitmap).shouldBeFalse()
    }

    @Test
    fun `saveBitmapToUri returns false when bitmap is null`() {
        ctx.saveBitmapToUri(null, null).shouldBeFalse()
    }

    @Test
    fun `exportBitmap returns false when launcher is null and saveLocation is CUSTOM`() {
        ctx.exportBitmap(SaveLocation.CUSTOM, bitmap, "test", null).shouldBeFalse()
    }

    @Test
    fun `toLocalizedString returns non-blank string for CUSTOM`() {
        SaveLocation.CUSTOM.toLocalizedString(ctx).shouldNotBeBlank()
    }

    @Test
    fun `toLocalizedString returns non-blank string for DOWNLOADS`() {
        SaveLocation.DOWNLOADS.toLocalizedString(ctx).shouldNotBeBlank()
    }

    @Test
    fun `toLocalizedString returns non-blank string for PICTURES`() {
        SaveLocation.PICTURES.toLocalizedString(ctx).shouldNotBeBlank()
    }

    @Test
    fun `toLocalizedString returns non-blank string for DCIM`() {
        SaveLocation.DCIM.toLocalizedString(ctx).shouldNotBeBlank()
    }

    @Test
    fun `getLocalizedEntries returns four entries`() {
        val entries = SaveLocation.getLocalizedEntries(ctx)
        assert(entries.size == 4)
    }
}
