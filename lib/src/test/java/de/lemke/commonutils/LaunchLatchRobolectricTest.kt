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

import android.Manifest.permission.CAMERA
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import de.lemke.commonutils.ui.utils.onSingleLaunchClick
import de.lemke.commonutils.ui.utils.singleLaunch
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.commonutils.ui.utils.singleLaunchMenuItem
import de.lemke.commonutils.ui.utils.singleLaunchSuspending
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity.IntentForResult

/** Rules and helpers that drive the launch latch of a [LatchTestActivity]. */
abstract class LaunchLatchRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    @get:Rule
    val drainMainLooper = DrainMainLooperRule()

    protected val shadowLooper get() = shadowOf(Looper.getMainLooper())

    protected fun resumed(): ActivityController<LatchTestActivity> = untrackedResumed().track(destroyActivities)

    protected fun untrackedResumed(): ActivityController<LatchTestActivity> =
        Robolectric.buildActivity(LatchTestActivity::class.java).setup()

    protected fun started(): ActivityController<LatchTestActivity> =
        Robolectric
            .buildActivity(LatchTestActivity::class.java)
            .create()
            .start()
            .postCreate(null)
            .track(destroyActivities)

    protected fun away(): ActivityController<LatchTestActivity> =
        resumed().also {
            it.get().launchScreen().shouldBeTrue()
            it.pause()
        }

    protected val ActivityController<LatchTestActivity>.observerCount
        get() = (get().lifecycle as LifecycleRegistry).observerCount

    protected fun Context.launchScreen(): Boolean = singleLaunchActivity(Intent(this, Activity::class.java))

    protected fun LatchTestActivity.launchForResult(): IntentForResult {
        launcher.launch(Intent(this, Activity::class.java))
        return shadowOf(this).nextStartedActivityForResult
    }

    protected fun LatchTestActivity.deliverResult(request: IntentForResult) {
        activityResultRegistry.dispatchResult(request.requestCode, RESULT_OK, null).shouldBeTrue()
    }

    /** A stale timer of an earlier launch would end this launch's second early. */
    protected fun LatchTestActivity.shouldHoldANewLaunchForOneSecond() {
        launchScreen().shouldBeTrue()
        shouldSettleAfterOneSecond()
    }

    protected fun LatchTestActivity.shouldSettleAfterOneSecond() {
        shadowLooper.idleFor(Duration.ofMillis(999))
        singleLaunch {}.shouldBeFalse()
        shadowLooper.idleFor(Duration.ofMillis(1))
        singleLaunch {}.shouldBeTrue()
    }

    protected fun LatchTestActivity.startBusy(): CompletableDeferred<Unit> {
        val work = CompletableDeferred<Unit>()
        singleLaunchSuspending(work = { work.await() }, then = {}).shouldBeTrue()
        return work
    }

    protected fun LatchTestActivity.addFragment(): LatchTestFragment =
        LatchTestFragment().also { supportFragmentManager.beginTransaction().add(android.R.id.content, it).commitNow() }
}
