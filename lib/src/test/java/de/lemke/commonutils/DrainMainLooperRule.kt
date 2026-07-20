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

import android.os.Looper
import java.time.Duration
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf

/**
 * Drains every pending main-Looper task after each test.
 *
 * Robolectric only resets its **own** shadowed framework/Looper state between tests — a real
 * third-party static singleton (e.g. `com.google.android.material.snackbar.SnackbarManager`) is
 * not a shadow and keeps whatever pending show/dismiss/animation Looper tasks a previous test
 * left it. Add `@get:Rule val drainMainLooper = DrainMainLooperRule()` to any Robolectric test
 * that drives such a singleton so no test leaves state behind for the next one.
 */
class DrainMainLooperRule : ExternalResource() {
    override fun after() {
        val shadow = shadowOf(Looper.getMainLooper())
        while (shadow.lastScheduledTaskTime != Duration.ZERO) {
            shadow.runToEndOfTasks()
        }
    }
}
