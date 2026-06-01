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
@file:Suppress("unused")

package de.lemke.commonutils

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity

private const val TAG = "OnboardingUtils"

/** Intent extra marking an activity launched as a step of the onboarding chain. */
const val EXTRA_ONBOARDING_STEP = "commonUtilsOnboardingStep"

/** Intent extra (honored only when the host opts in) that bypasses the onboarding chain, for benchmarks. */
const val EXTRA_SKIP_ONBOARDING = "commonUtilsSkipOnboarding"

/** Intent extra carrying the class name of the main activity to launch when the chain completes. */
const val EXTRA_ONBOARDING_MAIN_ACTIVITY = "commonUtilsOnboardingMainActivity"

/** Intent extra carrying the ordered list of step activity class names (excluding OOBE). */
const val EXTRA_ONBOARDING_STEPS = "commonUtilsOnboardingSteps"

private const val EXTRA_ONBOARDING_VERSION_CODE = "commonUtilsOnboardingVersionCode"
private const val EXTRA_ONBOARDING_VERSION_NAME = "commonUtilsOnboardingVersionName"
private const val EXTRA_ONBOARDING_APP_START_RESULT = "commonUtilsOnboardingAppStartResult"
private const val EXTRA_ONBOARDING_LAST_VERSION_CODE = "commonUtilsOnboardingLastVersionCode"
private const val EXTRA_ONBOARDING_LAST_VERSION_NAME = "commonUtilsOnboardingLastVersionName"
internal const val EXTRA_ONBOARDING_TOS_CHANGED = "commonUtilsOnboardingTosChanged"

/** Holds the ordered onboarding chain configuration. OOBE is always the implicit first step. */
object Onboarding {
    /** App-specific steps that run after OOBE, in order. */
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

/** The full ordered chain: OOBE first, then the configured steps. */
private fun onboardingChain(): List<Class<out Activity>> = listOf(CommonUtilsOOBEActivity::class.java) + Onboarding.steps

/** Returns the step after [current] in the chain, or `null` if [current] is the last step. Internal for unit testing. */
internal fun nextOnboardingStep(current: Class<*>): Class<out Activity>? {
    val chain = onboardingChain()
    val index = chain.indexOfFirst { it == current }
    if (index == -1) return null
    return chain.getOrNull(index + 1)
}

/**
 * Call as the FIRST thing in the launcher activity's `onCreate`, before inflating any UI.
 *
 * @return `null` if onboarding was launched (caller must `return` immediately). Otherwise the
 *   [AppStart] snapshot — valid for `isFirstTime`, `isFirstTimeVersion`, etc. even when called
 *   after the onboarding chain completes (the chain passes the original state back via Intent extras).
 *
 * When [allowSkip] is `true` and the launch intent carries [EXTRA_SKIP_ONBOARDING], the chain is
 * bypassed (used by benchmarks). [allowSkip] must be gated by the caller (e.g., a BuildConfig flag).
 */
fun AppCompatActivity.onboardIfNeeded(
    versionCode: Int,
    versionName: String,
    allowSkip: Boolean = false,
): AppStart? {
    // Post-onboarding: main activity re-launched after chain completed — reconstruct original AppStart.
    val appStartResultName = intent.getStringExtra(EXTRA_ONBOARDING_APP_START_RESULT)
    if (!appStartResultName.isNullOrEmpty()) {
        val result = AppStartResult.entries.find { it.name == appStartResultName }
        if (result != null) {
            val lastVersionCode = intent.getIntExtra(EXTRA_ONBOARDING_LAST_VERSION_CODE, -1)
            val lastVersionName = intent.getStringExtra(EXTRA_ONBOARDING_LAST_VERSION_NAME).orEmpty()
            val tosVersion = resources.getInteger(R.integer.commonutils_tos_version)
            return AppStart(
                result,
                versionCode,
                versionName,
                lastVersionCode,
                lastVersionName,
                tosVersion,
                commonUtilsSettings.acceptedTosVersion,
            )
        }
        Log.w(TAG, "onboardIfNeeded: unrecognized AppStartResult '$appStartResultName' — falling through to checkAppStart")
        intent.removeExtra(EXTRA_ONBOARDING_APP_START_RESULT)
    }
    val appStart = checkAppStart(versionCode, versionName)
    val shouldOnboard = appStart.shouldShowOOBE && !(allowSkip && intent.getBooleanExtra(EXTRA_SKIP_ONBOARDING, false))
    return if (shouldOnboard) {
        startActivity(
            Intent(this, CommonUtilsOOBEActivity::class.java).apply {
                putExtra(EXTRA_ONBOARDING_STEP, true)
                putExtra(EXTRA_ONBOARDING_MAIN_ACTIVITY, this@onboardIfNeeded::class.java.name)
                putStringArrayListExtra(EXTRA_ONBOARDING_STEPS, ArrayList(Onboarding.steps.map { it.name }))
                putExtra(EXTRA_ONBOARDING_VERSION_CODE, versionCode)
                putExtra(EXTRA_ONBOARDING_VERSION_NAME, versionName)
                putExtra(EXTRA_ONBOARDING_APP_START_RESULT, appStart.result.name)
                putExtra(EXTRA_ONBOARDING_LAST_VERSION_CODE, appStart.lastVersionCode)
                putExtra(EXTRA_ONBOARDING_LAST_VERSION_NAME, appStart.lastVersionName)
                putExtra(
                    EXTRA_ONBOARDING_TOS_CHANGED,
                    appStart.result == AppStartResult.FIRST_TIME_VERSION && !appStart.tosAccepted,
                )
            },
        )
        finishWithFade()
        null
    } else {
        commonUtilsSettings.lastVersionCode = versionCode
        commonUtilsSettings.lastVersionName = versionName
        appStart
    }
}

/** Completes onboarding, handling missing version code by reading from PackageManager. */
private fun Activity.completeOnboarding(mainActivityName: String) {
    val pendingVersionCode = intent.getIntExtra(EXTRA_ONBOARDING_VERSION_CODE, -1)
    if (pendingVersionCode == -1) {
        Log.w(TAG, "advanceOnboarding: EXTRA_ONBOARDING_VERSION_CODE missing — completing with NORMAL fallback")
        val packageInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        if (packageInfo == null) {
            Log.e(TAG, "completeOnboarding: getPackageInfo failed — cannot complete, finishing without commit")
            return
        }
        val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toInt()
        val currentVersionName = packageInfo.versionName.orEmpty()
        commonUtilsSettings.lastVersionCode = currentVersionCode
        commonUtilsSettings.lastVersionName = currentVersionName
        commonUtilsSettings.acceptedTosVersion = resources.getInteger(R.integer.commonutils_tos_version)
        startActivity(
            Intent().setClassName(this, mainActivityName).apply {
                putExtra(EXTRA_ONBOARDING_APP_START_RESULT, AppStartResult.NORMAL.name)
                putExtra(EXTRA_ONBOARDING_LAST_VERSION_CODE, intent.getIntExtra(EXTRA_ONBOARDING_LAST_VERSION_CODE, -1))
                putExtra(EXTRA_ONBOARDING_LAST_VERSION_NAME, intent.getStringExtra(EXTRA_ONBOARDING_LAST_VERSION_NAME).orEmpty())
            },
        )
    } else {
        commonUtilsSettings.lastVersionCode = pendingVersionCode
        commonUtilsSettings.lastVersionName = intent.getStringExtra(EXTRA_ONBOARDING_VERSION_NAME).orEmpty()
        commonUtilsSettings.acceptedTosVersion = resources.getInteger(R.integer.commonutils_tos_version)
        startActivity(
            Intent().setClassName(this, mainActivityName).apply {
                putExtra(EXTRA_ONBOARDING_APP_START_RESULT, intent.getStringExtra(EXTRA_ONBOARDING_APP_START_RESULT).orEmpty())
                putExtra(EXTRA_ONBOARDING_LAST_VERSION_CODE, intent.getIntExtra(EXTRA_ONBOARDING_LAST_VERSION_CODE, -1))
                putExtra(EXTRA_ONBOARDING_LAST_VERSION_NAME, intent.getStringExtra(EXTRA_ONBOARDING_LAST_VERSION_NAME).orEmpty())
            },
        )
    }
}

/**
 * Advances the onboarding chain from the current step: finishes this activity and starts the next
 * step (as task root, tagged [EXTRA_ONBOARDING_STEP]); or, past the last step, commits the
 * completion flag and starts the main activity.
 *
 * Call from a step activity when the user finishes that step.
 */
fun Activity.advanceOnboarding() {
    if (!isOnboardingStep()) {
        finishWithFade()
        return
    }
    val mainActivityName =
        checkNotNull(intent.getStringExtra(EXTRA_ONBOARDING_MAIN_ACTIVITY)) {
            "advanceOnboarding: EXTRA_ONBOARDING_MAIN_ACTIVITY missing — was this activity started by onboardIfNeeded?"
        }
    val stepsNames = intent.getStringArrayListExtra(EXTRA_ONBOARDING_STEPS) ?: arrayListOf()
    val chain = listOf(CommonUtilsOOBEActivity::class.java.name) + stepsNames
    val index = chain.indexOf(this::class.java.name)
    if (index != -1) {
        val nextName = chain.getOrNull(index + 1)
        if (nextName != null) {
            startActivity(
                Intent().setClassName(this, nextName).apply {
                    putExtra(EXTRA_ONBOARDING_STEP, true)
                    putExtra(EXTRA_ONBOARDING_MAIN_ACTIVITY, mainActivityName)
                    putStringArrayListExtra(EXTRA_ONBOARDING_STEPS, ArrayList(stepsNames))
                    putExtra(EXTRA_ONBOARDING_VERSION_CODE, intent.getIntExtra(EXTRA_ONBOARDING_VERSION_CODE, -1))
                    putExtra(EXTRA_ONBOARDING_VERSION_NAME, intent.getStringExtra(EXTRA_ONBOARDING_VERSION_NAME).orEmpty())
                    putExtra(EXTRA_ONBOARDING_APP_START_RESULT, intent.getStringExtra(EXTRA_ONBOARDING_APP_START_RESULT).orEmpty())
                    putExtra(EXTRA_ONBOARDING_LAST_VERSION_CODE, intent.getIntExtra(EXTRA_ONBOARDING_LAST_VERSION_CODE, -1))
                    putExtra(EXTRA_ONBOARDING_LAST_VERSION_NAME, intent.getStringExtra(EXTRA_ONBOARDING_LAST_VERSION_NAME).orEmpty())
                },
            )
        } else {
            completeOnboarding(mainActivityName)
        }
    } else {
        Log.w(TAG, "advanceOnboarding: ${this::class.java.simpleName} not found in chain — finishing without completing")
    }
    finishWithFade()
}

/** `true` if this activity was launched as a step of the onboarding chain (vs. standalone). */
fun Activity.isOnboardingStep(): Boolean = intent.getBooleanExtra(EXTRA_ONBOARDING_STEP, false)
