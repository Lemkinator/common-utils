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
package de.lemke.commonutils.ui.utils

import android.app.Activity
import android.content.Intent
import android.os.Parcelable
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import de.lemke.commonutils.R
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.domain.AppStart
import de.lemke.commonutils.domain.AppStartResult
import de.lemke.commonutils.domain.CheckAppStartUseCase
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity
import kotlinx.parcelize.Parcelize

private const val TAG = "OnboardingUtils"
private const val EXTRA_ONBOARDING_CONTEXT = "commonUtilsOnboardingContext"

/** Intent extra (honored only when the host opts in) that bypasses the onboarding chain, for benchmarks. */
const val EXTRA_SKIP_ONBOARDING = "commonUtilsSkipOnboarding"

/**
 * Holds the ordered onboarding chain configuration.
 *
 * OOBE ([CommonUtilsOOBEActivity]) is always the implicit first step and must not appear in [steps].
 */
object Onboarding {
    /** App-specific steps that run after OOBE in order. */
    var steps: List<Class<out Activity>> = emptyList()
        internal set
}

/**
 * Declares the app-specific onboarding steps that run after OOBE. Apps with OOBE only may skip this.
 *
 * Call in your `Application.onCreate()` to ensure steps are restored if the process is killed
 * and recreated during the onboarding flow.
 */
fun setupOnboarding(steps: List<Class<out Activity>> = emptyList()) {
    require(CommonUtilsOOBEActivity::class.java !in steps) {
        "CommonUtilsOOBEActivity is the implicit first step and must not be included in steps"
    }
    require(steps.distinct().size == steps.size) {
        "Onboarding steps must be unique; found duplicates: ${steps.groupBy { it }.filter { it.value.size > 1 }.keys}"
    }
    Onboarding.steps = steps
}

/**
 * Typed carrier for the full onboarding state, passed as one Intent extra and forwarded unchanged
 * across chain hops so the main activity can reconstruct the original [AppStart] on re-entry.
 *
 * @property mainActivityName Fully qualified class name of the launcher activity that started onboarding.
 * @property steps Ordered list of fully qualified class names for the app-specific onboarding steps.
 * @property versionCode The current app version code at the time onboarding was initiated.
 * @property versionName The current app version name at the time onboarding was initiated.
 * @property appStartResult The original [AppStartResult] from the fresh app-start check.
 * @property lastVersionCode The version code recorded on the previous launch.
 * @property lastVersionName The version name recorded on the previous launch.
 * @property tosChanged `true` if the TOS version changed since the user last accepted.
 */
@Parcelize
internal data class OnboardingContext(
    val mainActivityName: String,
    val steps: List<String>,
    val versionCode: Int,
    val versionName: String,
    val appStartResult: AppStartResult,
    val lastVersionCode: Int,
    val lastVersionName: String,
    val tosChanged: Boolean,
) : Parcelable

private fun Intent.putOnboardingContext(ctx: OnboardingContext): Intent = putExtra(EXTRA_ONBOARDING_CONTEXT, ctx)

/** Retrieves the [OnboardingContext] carrier from this intent, or `null` if not present. */
internal val Intent.onboardingContext: OnboardingContext?
    get() = IntentCompat.getParcelableExtra(this, EXTRA_ONBOARDING_CONTEXT, OnboardingContext::class.java)

/**
 * Persists version and TOS acceptance. The sole writing point for all three settings fields —
 * called in `onboardIfNeeded` whenever the app proceeds to main (post-onboarding or no-onboarding).
 */
private fun AppCompatActivity.commitAppStart(
    versionCode: Int,
    versionName: String,
    settings: SettingsRepository,
) {
    settings.lastVersionCode = versionCode
    settings.lastVersionName = versionName
    settings.acceptedTosVersion = resources.getInteger(R.integer.commonutils_tos_version)
}

/**
 * Call as the FIRST thing in the launcher activity's `onCreate`, after `super.onCreate`.
 *
 * @return `null` if onboarding was launched (caller must `return` immediately). Otherwise, the
 *   [AppStart] snapshot — valid for `isFirstTime`, `isFirstTimeVersion`, etc. even when called
 *   after the onboarding chain completes (the chain passes the original state back via the carrier).
 *
 * When [allowSkip] is `true` and the launch intent carries [EXTRA_SKIP_ONBOARDING], the chain is
 * bypassed (used by benchmarks). [allowSkip] must be gated by the caller (e.g., a BuildConfig flag).
 */
fun AppCompatActivity.onboardIfNeeded(
    versionCode: Int,
    versionName: String,
    settings: SettingsRepository,
    allowSkip: Boolean = false,
): AppStart? {
    val ctx = intent.onboardingContext
    val appStart =
        if (ctx != null) {
            // Post-onboarding: main activity re-launched after chain completed — reconstruct original AppStart.
            intent.removeExtra(EXTRA_ONBOARDING_CONTEXT)
            val tosVersion = resources.getInteger(R.integer.commonutils_tos_version)
            AppStart(
                ctx.appStartResult,
                versionCode,
                versionName,
                ctx.lastVersionCode,
                ctx.lastVersionName,
                tosVersion,
                tosVersion,
            ).also { Log.d(TAG, "onboardIfNeeded (post-onboarding): $it") }
        } else {
            val freshAppStart = CheckAppStartUseCase(this, settings)(versionCode, versionName)
            if (freshAppStart.shouldShowOOBE && !(allowSkip && intent.getBooleanExtra(EXTRA_SKIP_ONBOARDING, false))) {
                startActivity(
                    Intent(this, CommonUtilsOOBEActivity::class.java).putOnboardingContext(
                        OnboardingContext(
                            mainActivityName = this::class.java.name,
                            steps = Onboarding.steps.map { it.name },
                            versionCode = versionCode,
                            versionName = versionName,
                            appStartResult = freshAppStart.result,
                            lastVersionCode = freshAppStart.lastVersionCode,
                            lastVersionName = freshAppStart.lastVersionName,
                            tosChanged = freshAppStart.result == AppStartResult.FIRST_TIME_VERSION && !freshAppStart.tosAccepted,
                        ),
                    ),
                )
                finishWithFade()
                return null
            }
            freshAppStart
        }
    commitAppStart(versionCode, versionName, settings)
    overrideFadeOpenTransition()
    return appStart
}

/**
 * Returns the class name after [current] in [chain], or `null` when [current] is the last item.
 * Returns `null` (not found) when [current] is not in [chain]. Internal for unit testing.
 */
internal fun nextInChain(
    chain: List<String>,
    current: String,
): String? {
    val index = chain.indexOf(current)
    return if (index == -1) null else chain.getOrNull(index + 1)
}

/**
 * Advances the onboarding chain from the current step: starts the next step (forwarding the carrier
 * unchanged), or past the last step, starts the main activity. Then finishes this step.
 *
 * Call from a step activity when the user finishes that step. Safe to call from standalone context
 * (activity not launched as part of the chain) — just finishes the activity.
 */
fun Activity.advanceOnboarding() {
    val ctx = intent.onboardingContext
    if (ctx == null) {
        finishWithFade()
        return
    }
    val chain = listOf(CommonUtilsOOBEActivity::class.java.name) + ctx.steps
    val current = this::class.java.name
    if (!chain.contains(current)) {
        Log.w(TAG, "advanceOnboarding: ${this::class.java.simpleName} not in chain — finishing without advancing")
    } else {
        val next = nextInChain(chain, current)
        if (next != null) {
            startActivity(Intent().setClassName(this, next).putOnboardingContext(ctx))
        } else {
            completeOnboarding(ctx)
        }
    }
    finishWithFade()
}

/** Starts the main activity with the carrier so `onboardIfNeeded` can commit and reconstruct `AppStart`. */
private fun Activity.completeOnboarding(ctx: OnboardingContext) {
    startActivity(Intent().setClassName(this, ctx.mainActivityName).putOnboardingContext(ctx))
}

/** `true` if this activity was launched as a step of the onboarding chain (vs. standalone). */
fun Activity.isOnboardingStep(): Boolean = intent.onboardingContext != null
