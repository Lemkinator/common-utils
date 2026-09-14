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

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric can't drive the real predictive-back-gesture callback or Lottie's Choreographer
 * animator - this runs the About Me screen on a real device with both live.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class CommonUtilsAboutMeActivityTest {
    @Test
    fun activityLaunchesWithoutCrash() {
        ActivityScenario.launch(CommonUtilsAboutMeActivity::class.java).use { scenario ->
            scenario.state shouldBe Lifecycle.State.RESUMED
        }
    }
}
