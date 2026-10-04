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
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.MainThread
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Consumer
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.preference.Preference
import dev.oneuiproject.oneui.ktx.activity
import dev.oneuiproject.oneui.ktx.onClick
import java.util.Collections
import java.util.EnumSet
import java.util.WeakHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

private val LEAVING_TIMEOUT = 1.seconds

private val launchLatches = hashMapOf<Activity, LaunchLatch>()

/** Where an activity stands between a gated launch and its return to the foreground. */
private sealed interface LatchPhase {
    /** No gated launch is pending. */
    object Idle : LatchPhase

    /** A gated launch is pending and the activity has not paused since; [owner] is the result launcher that launched, if any. */
    class Leaving(
        val owner: ActivityResultLauncher<*>?,
    ) : LatchPhase

    /** The activity paused after a gated launch and has not resumed since. */
    object Away : LatchPhase
}

/** A delivery that may launch while the activity is [LatchPhase.Away]. */
private enum class LaunchSource {
    /** The callback of a [registerForSingleLaunchResult] launcher. */
    RESULT_CALLBACK,

    /** An `onNewIntent` delivery, until the end of its main-thread message. */
    NEW_INTENT,
}

/** A [singleLaunchSuspending] input whose work or `then` still runs; its coroutine carries it as a context element. */
private class HeldInput : AbstractCoroutineContextElement(HeldInput) {
    /** True once the activity stopped after this input was admitted, so views captured at the input may be stale. */
    var stopped = false

    companion object Key : CoroutineContext.Key<HeldInput>
}

/**
 * Launch latch of one activity: the View counterpart of Lifecycle's `dropUnlessResumed`.
 *
 * A gated launch moves the latch to [LatchPhase.Leaving], so the activity counts as not resumed until it pauses and
 * resumes again. Inputs run only while the activity is RESUMED, the latch is idle and no suspending input runs.
 */
private class LaunchLatch(
    private val activity: ComponentActivity,
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private val leavingTimeout = Runnable { phase = LatchPhase.Idle }
    private val newIntentListener = Consumer<Intent> { onNewIntent() }
    private val sources = EnumSet.noneOf(LaunchSource::class.java)
    private var phase: LatchPhase = LatchPhase.Idle
    private var heldInput: HeldInput? = null
    private var deliveringInput: HeldInput? = null
    private var resumed = activity.lifecycle.currentState == RESUMED

    val admitsInput: Boolean
        get() = activity.lifecycle.currentState == RESUMED && phase == LatchPhase.Idle && heldInput == null

    val admitsLaunch: Boolean
        get() =
            when (phase) {
                LatchPhase.Idle -> true
                is LatchPhase.Leaving -> false
                LatchPhase.Away -> sources.isNotEmpty()
            }

    /** True unless a held input's `then` runs and the activity stopped since that input was admitted. */
    val inputViewsCurrent: Boolean
        get() = deliveringInput?.stopped != true

    init {
        activity.lifecycle.addObserver(this)
        activity.addOnNewIntentListener(newIntentListener)
        PreResumeCallbacks.register(activity.application)
    }

    override fun onResume(owner: LifecycleOwner) {
        // A latch first created inside onResume or Fragment.onResume missed its pre-resume callback.
        if (!resumed) onPreResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        resumed = false
        if (phase is LatchPhase.Leaving) {
            handler.removeCallbacks(leavingTimeout)
            phase = LatchPhase.Away
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        heldInput?.stopped = true
    }

    override fun onDestroy(owner: LifecycleOwner) {
        handler.removeCallbacks(leavingTimeout)
        activity.lifecycle.removeObserver(this)
        activity.removeOnNewIntentListener(newIntentListener)
        launchLatches.remove(activity)
    }

    /** True if a launch from a coroutine with [context] passes the latch: [admitsLaunch], or the work of the held input. */
    fun admitsLaunchFrom(context: CoroutineContext): Boolean {
        val input = heldInput ?: return admitsLaunch
        return admitsLaunch || context[HeldInput] === input
    }

    /**
     * Runs [work] in [scope] as a held input, then [then] once the activity is RESUMED; see [singleLaunchSuspending].
     * @return true if the input was admitted.
     */
    fun <T> launchHeldInput(
        scope: CoroutineScope,
        work: suspend CoroutineScope.() -> T,
        then: (T) -> Unit,
    ): Boolean {
        if (!admitsInput) return false
        val input = HeldInput()
        heldInput = input
        scope
            .launch(input) {
                val result = work()
                activity.lifecycle.withResumed {
                    deliveringInput = input
                    try {
                        then(result)
                    } finally {
                        deliveringInput = null
                    }
                }
            }.invokeOnCompletion { heldInput = null }
        return true
    }

    fun launch(
        owner: ActivityResultLauncher<*>?,
        start: () -> Unit,
    ): Boolean {
        if (!admitsLaunch) return false
        val previous = phase
        phase = LatchPhase.Leaving(owner)
        if (resumed) armLeavingTimeout()
        runCatching(start)
            .onFailure {
                handler.removeCallbacks(leavingTimeout)
                phase = previous
            }.getOrThrow()
        return true
    }

    fun deliverResult(
        owner: ActivityResultLauncher<*>,
        callback: () -> Unit,
    ) {
        if ((phase as? LatchPhase.Leaving)?.owner === owner) settle()
        sources += LaunchSource.RESULT_CALLBACK
        try {
            callback()
        } finally {
            sources -= LaunchSource.RESULT_CALLBACK
        }
    }

    fun onPreResume() {
        resumed = true
        when (phase) {
            LatchPhase.Idle -> Unit
            is LatchPhase.Leaving -> armLeavingTimeout()
            LatchPhase.Away -> phase = LatchPhase.Idle
        }
    }

    private fun onNewIntent() {
        if (phase is LatchPhase.Leaving) settle()
        sources += LaunchSource.NEW_INTENT
        handler.postAtFrontOfQueue { sources -= LaunchSource.NEW_INTENT }
    }

    private fun armLeavingTimeout() {
        handler.removeCallbacks(leavingTimeout)
        handler.postDelayed(leavingTimeout, LEAVING_TIMEOUT.inWholeMilliseconds)
    }

    private fun settle() {
        handler.removeCallbacks(leavingTimeout)
        phase = LatchPhase.Idle
    }
}

/**
 * Reports the moment an activity starts to resume, before its `onResume` body and before its fragments resume.
 *
 * Below API 29 the application's `onActivityResumed` fires from `Activity.onResume`, which `super.onResume()` reaches
 * first; from API 29 on `onActivityPreResumed` fires before `onResume`.
 */
private object PreResumeCallbacks : Application.ActivityLifecycleCallbacks {
    private val registered = Collections.newSetFromMap(WeakHashMap<Application, Boolean>())

    override fun onActivityPreResumed(activity: Activity) {
        launchLatches[activity]?.onPreResume()
    }

    override fun onActivityResumed(activity: Activity) {
        if (SDK_INT < Q) launchLatches[activity]?.onPreResume()
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    fun register(application: Application) {
        if (registered.add(application)) application.registerActivityLifecycleCallbacks(this)
    }
}

/** The latch of the activity behind this context, or null without a live [ComponentActivity]. */
private val Context?.launchLatch: LaunchLatch?
    get() = (this?.activity as? ComponentActivity)?.takeUnless { it.lifecycle.currentState == DESTROYED }?.liveLaunchLatch

private val ComponentActivity.liveLaunchLatch: LaunchLatch
    get() = launchLatches.getOrPut(this) { LaunchLatch(this) }

/**
 * Returns false inside the `then` of a [singleLaunchSuspending] input if this context's activity stopped since the
 * input, because a view captured at the input may no longer be on screen; true otherwise.
 */
@MainThread
internal fun Context?.inputViewsCurrent(): Boolean = launchLatch?.inputViewsCurrent != false

/**
 * Returns false while this context's activity does not admit a launch, unless the caller runs as the work of its held
 * [singleLaunchSuspending] input; true otherwise.
 */
@MainThread
internal suspend fun Context.admitsLaunchPreparation(): Boolean =
    (activity as? ComponentActivity)?.let(launchLatches::get)?.admitsLaunchFrom(currentCoroutineContext()) != false

/** Runs [action] as an input and returns its result, or null if the latch dropped it; see [Context.singleLaunch]. */
@MainThread
internal fun <R : Any> Context?.singleLaunchOrNull(action: () -> R): R? = if (launchLatch?.admitsInput == false) null else action()

/**
 * Runs [start] as a gated launch of this context's activity; [owner] is the result launcher that launches, if any.
 * @return true if [start] ran, false if the latch dropped it.
 */
@MainThread
internal fun Context?.launchGated(
    owner: ActivityResultLauncher<*>? = null,
    start: () -> Unit,
): Boolean =
    when (val latch = launchLatch) {
        null -> true.also { start() }
        else -> latch.launch(owner, start)
    }

/**
 * Runs an input's [action], a tap or a selection, unless this context's activity is not RESUMED, a gated launch of
 * it is still pending, or a [singleLaunchSuspending] input still runs.
 *
 * The input itself changes nothing: only a gated launch inside it ([singleLaunchActivity], `transformToActivity`, a
 * [registerForSingleLaunchResult] launcher, or an open, share or email helper of this library) closes the latch until
 * the activity pauses and resumes again, or for about one second if no pause follows.
 * Without a live activity (an application or service context, a detached fragment, a destroyed activity) [action]
 * runs directly.
 * @return true if [action] ran, false if the latch dropped it.
 */
@MainThread
fun Context.singleLaunch(action: () -> Unit): Boolean = singleLaunchOrNull(action) != null

/** Runs an input's [action] through the launch latch of this fragment's activity; see [Context.singleLaunch]. */
@MainThread
fun Fragment.singleLaunch(action: () -> Unit): Boolean = context.singleLaunchOrNull(action) != null

/**
 * Runs an input that does async [work] in [lifecycleScope], then passes its result to [then] once this activity is
 * RESUMED, for a tap whose launch waits on work or whose work must not run twice.
 *
 * The input is admitted like [Context.singleLaunch]. Until [then] returns or the scope cancels the work, every other
 * input of this activity drops, also across a stop. [then] waits until the user is back if the work ends while the
 * activity is not RESUMED, and launches like any other gated launch.
 * @return true if [work] was launched, false if the latch dropped it.
 */
@MainThread
fun <T> ComponentActivity.singleLaunchSuspending(
    work: suspend CoroutineScope.() -> T,
    then: (T) -> Unit,
): Boolean = launchInput(lifecycleScope, work, then)

/**
 * Runs an input that does async [work] in the view lifecycle scope of this fragment through its activity's latch.
 *
 * [then] waits until the activity, not this fragment, is RESUMED, so an off-screen tab capped at STARTED still
 * launches. Destroying the view cancels [work]. Call it while the fragment has a view.
 * @see ComponentActivity.singleLaunchSuspending
 */
@MainThread
fun <T> Fragment.singleLaunchSuspending(
    work: suspend CoroutineScope.() -> T,
    then: (T) -> Unit,
): Boolean = requireActivity().launchInput(viewLifecycleOwner.lifecycleScope, work, then)

// Without a latch the activity is destroyed, and so is the scope: nothing would run.
private fun <T> ComponentActivity.launchInput(
    scope: CoroutineScope,
    work: suspend CoroutineScope.() -> T,
    then: (T) -> Unit,
): Boolean = launchLatch?.launchHeldInput(scope, work, then) ?: true

/**
 * Starts [intent] as a gated launch: unless an earlier gated launch of this context's activity is still pending, and,
 * once the activity paused after one, only from a [registerForSingleLaunchResult] callback or an `onNewIntent`
 * delivery until it resumes.
 *
 * A launch from `onCreate`, `onResume`, a RESUMED collector or a [singleLaunchSuspending] `then` runs. A launch that
 * throws reopens the latch and rethrows.
 * @return true if the activity was started, false if the latch dropped it.
 */
@MainThread
fun Context.singleLaunchActivity(
    intent: Intent,
    options: Bundle? = null,
): Boolean = launchGated { startActivity(intent, options) }

/**
 * Registers [contract] like `registerForActivityResult`, with a launcher whose launches are gated launches.
 *
 * Its own result ends its pending launch, also a result delivered without a pause (a granted permission), and
 * [callback] may launch even before this activity resumes.
 * @see singleLaunchActivity
 */
@MainThread
fun <I, O> ComponentActivity.registerForSingleLaunchResult(
    contract: ActivityResultContract<I, O>,
    callback: ActivityResultCallback<O>,
): ActivityResultLauncher<I> = SingleLaunchResultLauncher(this, contract, callback) { this }

/**
 * Registers [contract] with a launcher whose launches are gated launches of this fragment's activity.
 * @see ComponentActivity.registerForSingleLaunchResult
 */
@MainThread
fun <I, O> Fragment.registerForSingleLaunchResult(
    contract: ActivityResultContract<I, O>,
    callback: ActivityResultCallback<O>,
): ActivityResultLauncher<I> = SingleLaunchResultLauncher(this, contract, callback) { requireActivity() }

private class SingleLaunchResultLauncher<I, O>(
    caller: ActivityResultCaller,
    override val contract: ActivityResultContract<I, O>,
    callback: ActivityResultCallback<O>,
    private val host: () -> ComponentActivity,
) : ActivityResultLauncher<I>() {
    private val registered =
        caller.registerForActivityResult(contract) { result ->
            host().liveLaunchLatch.deliverResult(owner = this) { callback.onActivityResult(result) }
        }

    override fun launch(
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        host().launchGated(owner = this) { registered.launch(input, options) }
    }

    override fun unregister() = registered.unregister()
}

/** Sets a click listener that runs [action] as an input, for buttons and adapter rows; see [Context.singleLaunch]. */
fun View.onSingleLaunchClick(action: (View) -> Unit) {
    setOnClickListener { view -> view.context.singleLaunch { action(view) } }
}

/** Sets a click listener that runs [action] as an input; a dropped click stays consumed. See [Context.singleLaunch]. */
fun <P : Preference> P.onSingleLaunchClick(action: (P) -> Unit): P = apply { onClick<Preference> { context.singleLaunch { action(this) } } }

/**
 * Runs a menu item's [action] as an input, for `onOptionsItemSelected` and `onMenuItemSelected`.
 *
 * Wrap one handled branch, never the whole dispatch: a wrapped item never reaches another handler.
 * ```
 * override fun onOptionsItemSelected(item: MenuItem) =
 *     when (item.itemId) {
 *         R.id.menu_share -> singleLaunchMenuItem { shareApp() }
 *         else -> super.onOptionsItemSelected(item)
 *     }
 * ```
 * @return always true, so a dropped selection stays consumed.
 * @see Context.singleLaunch
 */
@MainThread
fun Context.singleLaunchMenuItem(action: () -> Unit): Boolean = true.also { singleLaunch(action) }
