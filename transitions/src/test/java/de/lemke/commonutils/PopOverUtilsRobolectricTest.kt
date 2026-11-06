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
import de.lemke.commonutils.ui.utils.singleLaunchPopOverActivity
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-xhdpi")
class PopOverUtilsRobolectricTest : LaunchLatchRobolectricTest() {
    @Test
    fun `class overload starts the component with the single top flag and no options off One UI`() {
        val activity = resumed().get()

        activity.singleLaunchPopOverActivity(Activity::class.java).shouldBeTrue()

        val started = shadowOf(activity).nextStartedActivityForResult.shouldNotBeNull()
        started.intent.component?.className shouldBe Activity::class.java.name
        (started.intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP) shouldBe Intent.FLAG_ACTIVITY_SINGLE_TOP
        started.options.shouldBeNull()
    }

    @Test
    fun `intent overload keeps the caller intent unchanged`() {
        val activity = resumed().get()
        val intent = Intent(activity, Activity::class.java)

        activity.singleLaunchPopOverActivity(intent).shouldBeTrue()

        intent.flags shouldBe 0
        shadowOf(activity).nextStartedActivityForResult.shouldNotBeNull()
    }

    @Test
    fun `a repeat tap during a pending launch is dropped`() {
        val activity = resumed().get()

        activity.singleLaunchPopOverActivity(Activity::class.java).shouldBeTrue()
        activity.singleLaunchPopOverActivity(Activity::class.java).shouldBeFalse()

        shadowOf(activity).nextStartedActivityForResult.shouldNotBeNull()
        shadowOf(activity).nextStartedActivityForResult.shouldBeNull()
    }
}
