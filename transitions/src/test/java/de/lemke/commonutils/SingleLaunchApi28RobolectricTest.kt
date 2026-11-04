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
import de.lemke.commonutils.ui.utils.singleLaunch
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SingleLaunchApi28RobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    @get:Rule
    val drainMainLooper = DrainMainLooperRule()

    @Test
    fun `away pre-resume settles idle before the onResume body below API 29`() {
        val controller =
            Robolectric
                .buildActivity(LatchTestActivity::class.java)
                .setup()
                .track(destroyActivities)
        val activity = controller.get()
        activity.singleLaunchActivity(Intent(activity, Activity::class.java)).shouldBeTrue()
        controller.pause()
        activity.singleLaunchActivity(Intent(activity, Activity::class.java)).shouldBeFalse()
        var launched: Boolean? = null
        activity.onResumeAction = { launched = activity.singleLaunchActivity(Intent(activity, Activity::class.java)) }

        controller.resume()

        launched shouldBe true
    }

    @Test
    fun `resume of an activity without a latch leaves other latches unchanged below API 29`() {
        val first =
            Robolectric
                .buildActivity(LatchTestActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        first.singleLaunch {}.shouldBeTrue()

        Robolectric
            .buildActivity(LatchTestActivity::class.java)
            .setup()
            .track(destroyActivities)

        first.singleLaunch {}.shouldBeTrue()
    }
}
