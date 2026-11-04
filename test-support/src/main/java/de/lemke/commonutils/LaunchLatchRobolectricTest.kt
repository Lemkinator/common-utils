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
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.lifecycle.LifecycleRegistry
import de.lemke.commonutils.ui.utils.singleLaunch
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.commonutils.ui.utils.singleLaunchSuspending
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowActivity.IntentForResult
import org.robolectric.shadows.ShadowLooper

private const val LEAVING_TIMEOUT_MILLIS = 1000L

/** Rules and helpers that drive the launch latch of a [LatchTestActivity]. */
public abstract class LaunchLatchRobolectricTest {
    @get:Rule
    public val destroyActivities: DestroyActivitiesRule = DestroyActivitiesRule()

    @get:Rule
    public val drainMainLooper: DrainMainLooperRule = DrainMainLooperRule()

    protected val shadowLooper: ShadowLooper get() = shadowOf(Looper.getMainLooper())

    protected val ActivityController<LatchTestActivity>.observerCount: Int
        get() = (get().lifecycle as LifecycleRegistry).observerCount

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
            assertTrue(it.get().launchScreen())
            it.pause()
        }

    protected fun Context.launchScreen(): Boolean = singleLaunchActivity(Intent(this, Activity::class.java))

    protected fun LatchTestActivity.launchForResult(): IntentForResult {
        launcher.launch(Intent(this, Activity::class.java))
        return shadowOf(this).nextStartedActivityForResult
    }

    protected fun LatchTestActivity.deliverResult(request: IntentForResult) {
        assertTrue(activityResultRegistry.dispatchResult(request.requestCode, RESULT_OK, null))
    }

    /** A stale timer of an earlier launch would end this launch's second early. */
    protected fun LatchTestActivity.shouldHoldANewLaunchForOneSecond() {
        assertTrue(launchScreen())
        shouldSettleAfterOneSecond()
    }

    protected fun LatchTestActivity.shouldSettleAfterOneSecond() {
        shadowLooper.idleFor(Duration.ofMillis(LEAVING_TIMEOUT_MILLIS - 1))
        assertFalse(singleLaunch {})
        shadowLooper.idleFor(Duration.ofMillis(1))
        assertTrue(singleLaunch {})
    }

    protected fun LatchTestActivity.startBusy(): CompletableDeferred<Unit> {
        val work = CompletableDeferred<Unit>()
        assertTrue(singleLaunchSuspending(work = { work.await() }, then = {}))
        return work
    }

    protected fun LatchTestActivity.addFragment(): LatchTestFragment =
        LatchTestFragment().also { supportFragmentManager.beginTransaction().add(android.R.id.content, it).commitNow() }
}
