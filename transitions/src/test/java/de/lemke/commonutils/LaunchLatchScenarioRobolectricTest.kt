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

import android.content.Intent
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.test.core.app.ActivityScenario
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaunchLatchScenarioRobolectricTest {
    @Test
    fun `returnFromLaunchedScreen lets the latch admit the next launch`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            scenario.read { it.singleLaunchActivity(Intent("first")) } shouldBe true
            scenario.read { it.singleLaunchActivity(Intent("second")) } shouldBe false

            scenario.returnFromLaunchedScreen()

            scenario.state shouldBe RESUMED
            scenario.read { it.singleLaunchActivity(Intent("third")) } shouldBe true
        }
    }
}
