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

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Robolectric has no Compose test infra here - this runs the real `setContent {}` on a real device. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class CommonUtilsLibsActivityTest {
    private val composeRule = createAndroidComposeRule<CommonUtilsLibsActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(IntentsRule()).around(composeRule)

    @Test
    fun activityLaunchesWithoutCrash() {
        composeRule.activityRule.scenario.state shouldBe Lifecycle.State.RESUMED
    }

    @Test
    fun sourceLinkOfGitOnlyScmUrlOpensItsWebPage() {
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        composeRule.onNodeWithText("Fixture Sudoku").performClick()
        composeRule.onNodeWithText("Source").performClick()

        intended(hasData("https://github.com/sfuhrm/sudoku"))
        composeRule.activityRule.scenario.state shouldBe Lifecycle.State.RESUMED
    }
}
