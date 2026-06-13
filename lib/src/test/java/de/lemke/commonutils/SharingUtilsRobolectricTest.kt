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
import io.kotest.matchers.booleans.shouldBeFalse
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class SharingUtilsRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `isSamsungQuickShareAvailable returns false when Quick Share not installed`() {
        ctx.isSamsungQuickShareAvailable().shouldBeFalse()
    }

    @Test
    fun `empty file list share returns false`() {
        emptyList<File>().share(ctx).shouldBeFalse()
    }
}
