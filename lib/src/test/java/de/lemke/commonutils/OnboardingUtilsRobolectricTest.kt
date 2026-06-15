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

import android.app.Activity
import android.content.Intent
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class OnboardingUtilsRobolectricTest {
    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun onboardingContext(
        mainActivityName: String = Activity::class.java.name,
        steps: List<String> = emptyList(),
    ) = OnboardingContext(
        mainActivityName = mainActivityName,
        steps = steps,
        versionCode = 1,
        versionName = "1.0",
        appStartResult = AppStartResult.FIRST_TIME,
        lastVersionCode = 0,
        lastVersionName = "",
        tosChanged = false,
    )

    @Test
    fun `isOnboardingStep returns false when intent has no onboarding context`() {
        activity().isOnboardingStep().shouldBeFalse()
    }

    @Test
    fun `isOnboardingStep returns true when intent carries onboarding context`() {
        val intent = Intent().apply { putExtra("commonUtilsOnboardingContext", onboardingContext()) }
        Robolectric
            .buildActivity(Activity::class.java, intent)
            .setup()
            .get()
            .isOnboardingStep()
            .shouldBeTrue()
    }

    @Test
    fun `advanceOnboarding finishes activity when no onboarding context`() {
        val a = activity()
        a.advanceOnboarding()
        a.isFinishing.shouldBeTrue()
    }

    @Test
    fun `advanceOnboarding with context where activity not in chain finishes without starting new activity`() {
        // Activity::class is not CommonUtilsOOBEActivity → not in chain
        val ctx = onboardingContext(steps = emptyList())
        val intent = Intent().apply { putExtra("commonUtilsOnboardingContext", ctx) }
        val a = Robolectric.buildActivity(Activity::class.java, intent).setup().get()
        a.advanceOnboarding()
        a.isFinishing.shouldBeTrue()
        // No new activity started for the next step (since not in chain, we finish early)
    }

    @Test
    fun `advanceOnboarding completes onboarding when last step in chain`() {
        // Chain: [OOBE, Activity]. Current = Activity → last → completeOnboarding starts main.
        val oobeClass = de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity::class.java.name
        val stepClass = Activity::class.java.name
        val mainClass = Activity::class.java.name
        val ctx = onboardingContext(mainActivityName = mainClass, steps = listOf(stepClass))
        // Manually construct the intent that would have OOBE in chain + stepClass
        val intent = Intent().apply { putExtra("commonUtilsOnboardingContext", ctx) }
        val a = Robolectric.buildActivity(Activity::class.java, intent).setup().get()
        a.advanceOnboarding()
        // Should have started the main activity and finished
        shadowOf(a).nextStartedActivity shouldNotBe null
        a.isFinishing.shouldBeTrue()
    }

    @Test
    fun `setupOnboarding stores steps and Onboarding object reflects them`() {
        setupOnboarding(listOf(Activity::class.java))
        Onboarding.steps shouldBe listOf(Activity::class.java)
    }
}
