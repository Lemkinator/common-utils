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

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.app.Activity
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import androidx.appcompat.app.AppCompatActivity
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity

/** Intent extra marking an activity launched as a step of the first-run chain. */
const val EXTRA_FIRST_RUN = "commonUtilsFirstRunStep"

/** Intent extra (honored only when the host opts in) that bypasses the first-run chain, for benchmarks. */
const val EXTRA_SKIP_FIRST_RUN = "commonUtilsSkipFirstRun"

/** Holds the ordered first-run chain configuration. OOBE is always the implicit first step. */
object FirstRunFlow {
    /** App-specific steps that run after OOBE, in order. */
    var steps: List<Class<out Activity>> = emptyList()

    /** The activity to land on once the chain completes (set by [handleFirstRun]). */
    var mainActivity: Class<out Activity>? = null
}

/** Declares the app-specific first-run steps that run after OOBE. Apps with OOBE only may skip this. */
fun setupFirstRunFlow(steps: List<Class<out Activity>> = emptyList()) {
    FirstRunFlow.steps = steps
}

/** The full ordered chain: OOBE first, then the configured steps. */
private fun firstRunChain(): List<Class<out Activity>> = listOf(CommonUtilsOOBEActivity::class.java) + FirstRunFlow.steps

/** Returns the step after [current] in the chain, or `null` if [current] is the last step. */
internal fun nextFirstRunStep(current: Class<*>): Class<out Activity>? {
    val chain = firstRunChain()
    val index = chain.indexOfFirst { it == current }
    if (index == -1) return null
    return chain.getOrNull(index + 1)
}

/**
 * Call as the FIRST thing in the launcher activity's `onCreate`, before inflating any UI.
 *
 * @return `true` if a first run was detected and OOBE was launched (the caller must `return`
 *   immediately so no UI is built). `false` for a normal start (proceed to build the activity).
 *
 * When [allowSkip] is `true` and the launch intent carries [EXTRA_SKIP_FIRST_RUN], the chain is
 * bypassed (used by benchmarks). [allowSkip] must be gated by the caller (e.g. a BuildConfig flag).
 */
fun AppCompatActivity.handleFirstRun(
    versionCode: Int,
    versionName: String,
    allowSkip: Boolean = false,
): Boolean {
    if (allowSkip && intent.getBooleanExtra(EXTRA_SKIP_FIRST_RUN, false)) return false
    if (!checkAppStart(versionCode, versionName).shouldShowOOBE) return false
    FirstRunFlow.mainActivity = this::class.java
    startActivity(Intent(this, CommonUtilsOOBEActivity::class.java).putExtra(EXTRA_FIRST_RUN, true))
    @Suppress("DEPRECATION")
    if (SDK_INT < UPSIDE_DOWN_CAKE) overridePendingTransition(fade_in, fade_out)
    finishAfterTransition()
    return true
}

/**
 * Advances the first-run chain from the current step: finishes this activity and starts the next
 * step (as task root, tagged [EXTRA_FIRST_RUN]); or, past the last step, commits the completion
 * flag and starts the main activity.
 *
 * Call from a step activity when the user finishes that step.
 */
fun Activity.advanceFirstRun() {
    val next = nextFirstRunStep(this::class.java)
    if (next != null) {
        startActivity(Intent(this, next).putExtra(EXTRA_FIRST_RUN, true))
    } else {
        commonUtilsSettings.acceptedTosVersion = resources.getInteger(R.integer.commonutils_tos_version)
        checkNotNull(FirstRunFlow.mainActivity) {
            "advanceFirstRun: no mainActivity configured — call handleFirstRun() from the launcher activity's onCreate before the first-run chain starts"
        }
        startActivity(Intent(this, FirstRunFlow.mainActivity!!))
    }
    @Suppress("DEPRECATION")
    if (SDK_INT < UPSIDE_DOWN_CAKE) overridePendingTransition(fade_in, fade_out)
    finishAfterTransition()
}

/** `true` if this activity was launched as a step of the first-run chain (vs. standalone). */
fun Activity.isFirstRunStep(): Boolean = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
