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

import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RobolectricWaitsRobolectricTest {
    @Test
    fun `idleMainLooper runs the tasks posted to the main looper`() {
        val ran = AtomicBoolean(false)
        Handler(Looper.getMainLooper()).post { ran.set(true) }
        ran.get() shouldBe false

        idleMainLooper()

        ran.get() shouldBe true
    }

    @Test
    fun `awaitMainLooperUntil returns at once when the condition holds`() {
        awaitMainLooperUntil { true }
    }

    @Test
    fun `awaitMainLooperUntil idles the looper between checks`() {
        val ran = AtomicBoolean(false)
        Handler(Looper.getMainLooper()).post { ran.set(true) }

        awaitMainLooperUntil { ran.get() }
    }

    @Test
    fun `awaitMainLooperUntil waits for work on a background thread that posts to the main looper`() {
        val ran = AtomicBoolean(false)
        thread {
            Thread.sleep(50)
            Handler(Looper.getMainLooper()).post { ran.set(true) }
        }

        awaitMainLooperUntil { ran.get() }
    }

    @Test
    fun `awaitMainLooperUntil throws once the timeout has passed`() {
        val error =
            shouldThrow<IllegalStateException> {
                awaitMainLooperUntil(timeout = 30.milliseconds, poll = 5.milliseconds) { false }
            }

        error.message shouldBe "condition not met within 30ms"
    }

    @Test
    fun `read returns the value the block computes on the activity`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            scenario.read { it.javaClass.simpleName } shouldBe "LatchTestActivity"
        }
    }
}
