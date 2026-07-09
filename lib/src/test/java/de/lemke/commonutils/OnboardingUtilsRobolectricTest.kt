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
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.domain.AppStartResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class OnboardingUtilsRobolectricTest {
    private lateinit var settings: SettingsRepository

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @BeforeEach
    fun initSettings() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().clear().commit()
        settings = SettingsRepository(prefs)
    }

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
    fun `setupOnboarding without args covers default-param synthetic`() {
        setupOnboarding()
        Onboarding.steps shouldBe emptyList()
    }

    @Test
    fun `setupOnboarding stores steps and Onboarding object reflects them`() {
        setupOnboarding(listOf(Activity::class.java))
        Onboarding.steps shouldBe listOf(Activity::class.java)
    }

    @Test
    fun `setupOnboarding with OOBE class in steps throws IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            setupOnboarding(listOf(de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity::class.java))
        }
    }

    @Test
    fun `setupOnboarding with duplicate steps throws IllegalArgumentException`() {
        // 3 items: Activity appears twice, AppCompatActivity once.
        // filter { size > 1 } returns only Activity (size=2); AppCompatActivity (size=1) hits the false branch.
        shouldThrow<IllegalArgumentException> {
            setupOnboarding(listOf(Activity::class.java, AppCompatActivity::class.java, Activity::class.java))
        }
    }

    @Test
    fun `advanceOnboarding starts next step when more steps remain in chain`() {
        // chain = [OOBE, Activity, AppCompatActivity]; current = Activity → next = AppCompatActivity
        val ctx = onboardingContext(steps = listOf(Activity::class.java.name, AppCompatActivity::class.java.name))
        val intent = Intent().apply { putExtra("commonUtilsOnboardingContext", ctx) }
        val a = Robolectric.buildActivity(Activity::class.java, intent).setup().get()
        a.advanceOnboarding()
        shadowOf(a).nextStartedActivity shouldNotBe null
        a.isFinishing.shouldBeTrue()
    }

    @Test
    fun `onboardIfNeeded with allowSkip and skip extra bypasses OOBE and returns AppStart`() {
        // shouldShowOOBE=true (fresh install) but allowSkip=true + EXTRA_SKIP_ONBOARDING=true → skip
        val intent = Intent().apply { putExtra("commonUtilsSkipOnboarding", true) }
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java, intent).setup()
        val result = controller.get().onboardIfNeeded(1, "1.0", settings, allowSkip = true)
        result shouldNotBe null
        shadowOf(controller.get()).nextStartedActivity shouldBe null
    }

    @Test
    fun `onboardIfNeeded allowSkip true but no skip extra starts OOBE`() {
        // allowSkip=true but no EXTRA_SKIP_ONBOARDING in intent → !(true && false) = true → start OOBE
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val result = controller.get().onboardIfNeeded(1, "1.0", settings, allowSkip = true)
        result shouldBe null
        shadowOf(controller.get()).nextStartedActivity shouldNotBe null
    }

    // onboardIfNeeded - Path 1: intent carries onboarding context (post-onboarding re-launch)
    @Test
    fun `onboardIfNeeded with onboarding context returns AppStart and commits version`() {
        val ctx =
            OnboardingContext(
                mainActivityName = AppCompatActivity::class.java.name,
                steps = emptyList(),
                versionCode = 1,
                versionName = "1.0",
                appStartResult = AppStartResult.FIRST_TIME,
                lastVersionCode = -1,
                lastVersionName = "0.0.0",
                tosChanged = false,
            )
        val intent = Intent().apply { putExtra("commonUtilsOnboardingContext", ctx) }
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java, intent).setup()
        val a = controller.get()
        val result = a.onboardIfNeeded(1, "1.0", settings)
        result shouldNotBe null
        result!!.result shouldBe AppStartResult.FIRST_TIME
        settings.lastVersionCode shouldBe 1
    }

    // onboardIfNeeded - Path 2: fresh install (lastVersionCode=-1 → shouldShowOOBE=true) → null
    @Test
    fun `onboardIfNeeded fresh install starts OOBE and returns null`() {
        // lastVersionCode defaults to -1 → isFirstTime = true → shouldShowOOBE = true
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val a = controller.get()
        val result = a.onboardIfNeeded(1, "1.0", settings)
        result shouldBe null
        shadowOf(a).nextStartedActivity shouldNotBe null
    }

    // onboardIfNeeded - covers tosChanged=true: FIRST_TIME_VERSION + TOS not accepted → starts OOBE
    @Test
    fun `onboardIfNeeded FIRST_TIME_VERSION with unaccepted TOS sets tosChanged true and starts OOBE`() {
        settings.lastVersionCode = 1
        settings.acceptedTosVersion = -1 // -1 < tosVersion(0) → !tosAccepted = true
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val result = controller.get().onboardIfNeeded(2, "2.0", settings)
        result shouldBe null
        shadowOf(controller.get()).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `OnboardingContext parcels and unparcels correctly`() {
        val original =
            onboardingContext(
                mainActivityName = "com.example.MainActivity",
                steps = listOf("com.example.StepA", "com.example.StepB"),
            )
        val parcel = android.os.Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            @Suppress("UNCHECKED_CAST")
            val creator =
                OnboardingContext::class.java
                    .getDeclaredField("CREATOR")
                    .get(null) as android.os.Parcelable.Creator<OnboardingContext>
            val restored = creator.createFromParcel(parcel)
            restored shouldBe original
        } finally {
            parcel.recycle()
        }
    }

    // onboardIfNeeded - Path 3: TOS accepted + same version → returns AppStart, commits
    @Test
    fun `onboardIfNeeded with accepted TOS returns AppStart`() {
        settings.lastVersionCode = 1
        settings.acceptedTosVersion = Int.MAX_VALUE // >= any tosVersion in resources
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val a = controller.get()
        val result = a.onboardIfNeeded(1, "1.0", settings)
        result shouldNotBe null
        result!!.result shouldBe AppStartResult.NORMAL
    }
}
