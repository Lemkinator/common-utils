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

import android.content.ActivityNotFoundException
import android.content.Intent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class RecordingIntentLauncherTest : ShouldSpec(
    {
        val first = Intent("first")
        val second = Intent("second")

        should("admit and record each launch in order") {
            val launcher = RecordingIntentLauncher()

            launcher.tryLaunch(first) shouldBe true
            launcher.tryLaunch(second) shouldBe true

            launcher.launched shouldBe listOf(first, second)
        }

        should("refuse and not record a launch while admits is false") {
            val launcher = RecordingIntentLauncher(admits = false)

            launcher.tryLaunch(first) shouldBe false

            launcher.launched.shouldBeEmpty()
        }

        should("admit again after admits is set back to true") {
            val launcher = RecordingIntentLauncher(admits = false)
            launcher.tryLaunch(first)
            launcher.admits = true

            launcher.tryLaunch(second) shouldBe true

            launcher.launched shouldBe listOf(second)
        }

        should("throw the failure and record nothing while a failure is set") {
            val failure = ActivityNotFoundException("no picker")
            val launcher = RecordingIntentLauncher(failure)

            shouldThrow<ActivityNotFoundException> { launcher.tryLaunch(first) } shouldBe failure

            launcher.launched.shouldBeEmpty()
        }

        should("launch normally after the failure is cleared") {
            val launcher = RecordingIntentLauncher(IllegalStateException("boom"))
            launcher.failure = null

            launcher.tryLaunch(first) shouldBe true

            launcher.launched shouldBe listOf(first)
        }

        should("unregister without effect") {
            RecordingIntentLauncher().unregister()
        }
    },
)
