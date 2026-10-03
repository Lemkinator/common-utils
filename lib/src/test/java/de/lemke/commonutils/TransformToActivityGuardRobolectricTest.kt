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
import android.view.View
import de.lemke.commonutils.ui.utils.TRANSITION_NAME_KEY
import de.lemke.commonutils.ui.utils.singleLaunch
import de.lemke.commonutils.ui.utils.singleLaunchSuspending
import de.lemke.commonutils.ui.utils.transformToActivity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity.IntentForResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TransformToActivityGuardRobolectricTest : LaunchLatchRobolectricTest() {
    private fun LatchTestActivity.attachedView(): View = View(this).also(::setContentView)

    private fun LatchTestActivity.transformOnWorkEnd(view: View): CompletableDeferred<Unit> {
        val work = CompletableDeferred<Unit>()
        singleLaunchSuspending(
            work = { work.await() },
            then = { view.transformToActivity(Intent(this, Activity::class.java), "shared").shouldBeTrue() },
        ).shouldBeTrue()
        return work
    }

    private fun LatchTestActivity.startedLaunch(): IntentForResult = shadowOf(this).nextStartedActivityForResult.shouldNotBeNull()

    private fun IntentForResult.shouldUseTheTransition(view: View) {
        intent.getStringExtra(TRANSITION_NAME_KEY) shouldBe "shared"
        options.shouldNotBeNull()
        view.transitionName shouldBe "shared"
    }

    private fun IntentForResult.shouldSkipTheTransition(view: View) {
        intent.component?.className shouldBe Activity::class.java.name
        intent.hasExtra(TRANSITION_NAME_KEY) shouldBe false
        options.shouldBeNull()
        view.transitionName.shouldBeNull()
    }

    @Test
    fun `attached view starts the activity with the transition`() {
        val activity = resumed().get()
        val view = activity.attachedView()

        view.transformToActivity(Intent(activity, Activity::class.java), "shared").shouldBeTrue()

        activity.startedLaunch().shouldUseTheTransition(view)
    }

    @Test
    fun `detached view starts the activity without the transition`() {
        val activity = resumed().get()
        val view = View(activity)

        view.transformToActivity(Intent(activity, Activity::class.java), "shared").shouldBeTrue()

        activity.startedLaunch().shouldSkipTheTransition(view)
    }

    @Test
    fun `view detached during the work starts the activity without the transition`() {
        val activity = resumed().get()
        val view = activity.attachedView()
        val work = activity.transformOnWorkEnd(view)

        activity.setContentView(View(activity))
        work.complete(Unit)
        shadowLooper.idle()

        activity.startedLaunch().shouldSkipTheTransition(view)
    }

    @Test
    fun `suspending input without a stop starts the activity with the transition`() {
        val activity = resumed().get()
        val view = activity.attachedView()
        val work = activity.transformOnWorkEnd(view)

        work.complete(Unit)
        shadowLooper.idle()

        activity.startedLaunch().shouldUseTheTransition(view)
    }

    @Test
    fun `stop between tap and launch starts the activity without the transition`() {
        val controller = resumed()
        val activity = controller.get()
        val view = activity.attachedView()
        val work = activity.transformOnWorkEnd(view)

        controller.pause().stop()
        work.complete(Unit)
        shadowLooper.idle()
        shadowOf(activity).nextStartedActivityForResult.shouldBeNull()
        controller.restart().resume()

        activity.startedLaunch().shouldSkipTheTransition(view)
    }

    @Test
    fun `pause without a stop between tap and launch keeps the transition`() {
        val controller = resumed()
        val activity = controller.get()
        val view = activity.attachedView()
        val work = activity.transformOnWorkEnd(view)

        controller.pause()
        work.complete(Unit)
        shadowLooper.idle()
        controller.resume()

        activity.startedLaunch().shouldUseTheTransition(view)
    }

    @Test
    fun `stop before the tap keeps the transition`() {
        val controller = resumed()
        val activity = controller.get()
        val view = activity.attachedView()
        controller
            .pause()
            .stop()
            .restart()
            .resume()
        val work = activity.transformOnWorkEnd(view)

        work.complete(Unit)
        shadowLooper.idle()

        activity.startedLaunch().shouldUseTheTransition(view)
    }

    @Test
    fun `then that throws after a stop leaves a later launch its transition`() {
        val controller = resumed()
        val activity = controller.get()
        val work = CompletableDeferred<Unit>()
        activity.singleLaunchSuspending(work = { work.await() }, then = { error("then failed") }).shouldBeTrue()
        controller.pause().stop()
        work.complete(Unit)
        shadowLooper.idle()

        val failure =
            shouldThrow<IllegalStateException> {
                runTest {
                    controller.restart().resume()
                    shadowLooper.idle()
                }
            }

        failure.message shouldBe "then failed"
        activity.singleLaunch {}.shouldBeTrue()
        val view = activity.attachedView()
        view.transformToActivity(Intent(activity, Activity::class.java), "shared").shouldBeTrue()

        activity.startedLaunch().shouldUseTheTransition(view)
    }

    @Test
    fun `launch after a stopped input uses the transition again`() {
        val controller = resumed()
        val activity = controller.get()
        val stale = activity.attachedView()
        val work = activity.transformOnWorkEnd(stale)
        controller.pause().stop()
        work.complete(Unit)
        controller.restart().resume()
        shadowLooper.idle()
        activity.startedLaunch().shouldSkipTheTransition(stale)
        controller.pause()
        controller.resume()
        val view = activity.attachedView()

        view.transformToActivity(Intent(activity, Activity::class.java), "shared").shouldBeTrue()

        activity.startedLaunch().shouldUseTheTransition(view)
    }
}
