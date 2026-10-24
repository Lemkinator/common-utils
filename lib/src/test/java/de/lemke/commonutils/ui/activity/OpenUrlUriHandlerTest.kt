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
package de.lemke.commonutils.ui.activity

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.StartActivityFailingContext
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenUrlUriHandlerTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `openUri opens the web page of a git-only SCM URL`() {
        OpenUrlUriHandler(ctx).openUri("git@github.com:sfuhrm/sudoku.git")
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .nextStartedActivity.data
            .toString() shouldBe "https://github.com/sfuhrm/sudoku"
    }

    @Test
    fun `openUri shows the no-browser toast when no app opens the link`() {
        val failing = StartActivityFailingContext(ctx, ActivityNotFoundException("no browser"))
        OpenUrlUriHandler(failing).openUri("git@github.com:sfuhrm/sudoku.git")
        ShadowToast.getTextOfLatestToast() shouldBe "No browser app installed…"
    }
}
