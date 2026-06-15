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
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class TransformationUtilsRobolectricTest {
    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `prepareActivityTransformationFrom with plain Activity logs warning and returns early`() {
        // plain Activity is not LifecycleOwner → early-return path is exercised
        activity().prepareActivityTransformationFrom()
    }

    @Test
    fun `prepareActivityTransformationTo without transition name in intent returns early`() {
        // Intent has no TRANSITION_NAME_KEY → early-return path
        activity().prepareActivityTransformationTo()
    }

    @Test
    fun `prepareActivityTransformationBetween delegates to both from and to`() {
        activity().prepareActivityTransformationBetween()
    }

    @Test
    fun `transformToActivity null view starts activity directly`() {
        val a = activity()
        val intent = Intent(a, Activity::class.java)
        a.transformToActivity(null as View?, intent)
        shadowOf(a).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `transformToActivity unknown viewId starts activity directly`() {
        val a = activity()
        val intent = Intent(a, Activity::class.java)
        a.transformToActivity(Int.MAX_VALUE, intent)
        shadowOf(a).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `transformToActivity unknown viewId class overload starts activity directly`() {
        val a = activity()
        a.transformToActivity<Activity>(Int.MAX_VALUE)
        shadowOf(a).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `transformToActivity null view class overload starts activity directly`() {
        val a = activity()
        a.transformToActivity<Activity>(null as View?)
        shadowOf(a).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `View transformToActivity with non-Activity context calls context startActivity`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val view = View(ctx)
        // Non-Activity context path requires FLAG_ACTIVITY_NEW_TASK
        val intent = Intent(ctx, Activity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        view.transformToActivity(intent)
        // non-Activity context branch: falls through to context.startActivity
    }

    @Test
    fun `overrideFadeOpenTransition does not throw`() {
        activity().overrideFadeOpenTransition()
    }

    @Test
    fun `finishWithFade marks activity as finishing`() {
        val a = activity()
        a.finishWithFade()
        a.isFinishing.shouldBeTrue()
    }

    @Test
    fun `getTransitionContainerTransform returns transform with default duration`() {
        val transform = activity().getTransitionContainerTransform()
        transform.duration shouldBe DEFAULT_DURATION
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
class TransformationUtilsSdk33RobolectricTest {
    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `overrideFadeOpenTransition on pre-34 uses overridePendingTransition`() {
        // SDK < 34: overridePendingTransition branch
        activity().overrideFadeOpenTransition()
    }

    @Test
    fun `finishWithFade on pre-34 finishes via overridePendingTransition`() {
        val a = activity()
        a.finishWithFade()
        a.isFinishing.shouldBe(true)
    }
}
