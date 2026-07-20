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
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.DEFAULT_DURATION
import de.lemke.commonutils.ui.utils.DEFAULT_FADE_MODE
import de.lemke.commonutils.ui.utils.finishWithFade
import de.lemke.commonutils.ui.utils.getContainerTransform
import de.lemke.commonutils.ui.utils.getTransitionContainerTransform
import de.lemke.commonutils.ui.utils.overrideFadeOpenTransition
import de.lemke.commonutils.ui.utils.performTransform
import de.lemke.commonutils.ui.utils.prepareActivityTransformationBetween
import de.lemke.commonutils.ui.utils.prepareActivityTransformationFrom
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.transformTo
import de.lemke.commonutils.ui.utils.transformToActivity
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
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
        shadowOf(ctx as Application).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `View transformToActivity with AppCompatActivity context covers Activity path`() {
        val a = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val view = View(a)
        val intent = Intent(a, AppCompatActivity::class.java)
        view.transformToActivity(intent)
        shadowOf(a).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `transformToActivity with non-null view covers view-based branch`() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = View(a)
        view.id = android.R.id.text1
        a.setContentView(view)
        val intent = Intent(a, Activity::class.java)
        a.transformToActivity(view, intent)
        shadowOf(a).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `performTransform without duration and fadeMode covers default-param synthetic`() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val container = FrameLayout(a)
        val v1 = View(a)
        val v2 = View(a)
        container.addView(v1)
        container.addView(v2)
        a.setContentView(container)
        v1.performTransform(container, v2)
    }

    @Test
    fun `getContainerTransform without duration and fadeMode covers default-param synthetic`() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val v1 = View(a)
        val v2 = View(a)
        v1.getContainerTransform(v2)
    }

    @Test
    fun `View transformTo animates between views in a container`() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        // Attach container to window so View.post() actually posts to the Looper
        val container = FrameLayout(a)
        val v1 = View(a)
        val v2 = View(a)
        container.addView(v1)
        container.addView(v2)
        a.setContentView(container)
        v1.transformTo(v2)
        // Flush the posted runnable
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `performTransform directly covers getContainerTransform and transition logic`() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val container = FrameLayout(a)
        val v1 = View(a)
        val v2 = View(a)
        container.addView(v1)
        container.addView(v2)
        a.setContentView(container)
        v1.performTransform(container, v2, DEFAULT_DURATION, DEFAULT_FADE_MODE)
    }

    @Test
    fun `getContainerTransform returns MaterialContainerTransform with correct settings`() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val v1 = View(a)
        val v2 = View(a)
        val transform = v1.getContainerTransform(v2, 500L, DEFAULT_FADE_MODE)
        transform.duration shouldBe 500L
    }

    @Test
    fun `Activity transformToActivity with found viewId triggers view transition`() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = View(a)
        view.id = android.R.id.text1
        a.setContentView(view)
        val intent = Intent(a, Activity::class.java)
        a.transformToActivity(android.R.id.text1, intent)
        shadowOf(a).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `Activity transformToActivity class overload delegates to intent overload`() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        // Int.MAX_VALUE view not found → plain startActivity fallback
        a.transformToActivity(Int.MAX_VALUE, Activity::class.java)
        shadowOf(a).nextStartedActivity shouldNotBe null
    }

    @Test
    fun `View transformToActivity cls overload starts activity via Activity context`() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = View(a)
        view.transformToActivity(Activity::class.java)
        shadowOf(a).nextStartedActivity shouldNotBe null
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

    @Test
    fun `prepareActivityTransformationTo with transition name on plain Activity warns and returns early`() {
        // Plain Activity is NOT a LifecycleOwner → hits the LifecycleOwner null-check warning path
        val intent = Intent().apply { putExtra("commonUtilsTransitionNameKey", "testTransition") }
        Robolectric
            .buildActivity(Activity::class.java, intent)
            .setup()
            .get()
            .prepareActivityTransformationTo()
    }

    @Test
    fun `prepareActivityTransformationFrom onDestroy while finishing clears exit callback`() {
        // Must call before create() - requestFeature() must precede window content setup
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        val a = controller.get()
        a.prepareActivityTransformationFrom()
        controller.setup() // create/start/resume after feature is requested
        a.finish()
        controller.destroy()
    }

    @Test
    fun `prepareActivityTransformationFrom onDestroy when not finishing skips exit callback clearing`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        val a = controller.get()
        a.prepareActivityTransformationFrom()
        controller.setup()
        // No finish() → isFinishing = false in onDestroy → if body skipped
        controller.destroy()
    }

    @Test
    fun `prepareActivityTransformationTo onDestroy while finishing clears enter callback`() {
        val intent = Intent().apply { putExtra("commonUtilsTransitionNameKey", "testTransition") }
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java, intent)
        val a = controller.get()
        a.prepareActivityTransformationTo()
        controller.setup()
        a.finish()
        controller.destroy()
    }

    @Test
    fun `prepareActivityTransformationTo onDestroy when not finishing skips enter callback clearing`() {
        val intent = Intent().apply { putExtra("commonUtilsTransitionNameKey", "testTransition") }
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java, intent)
        val a = controller.get()
        a.prepareActivityTransformationTo()
        controller.setup()
        // No finish() → isFinishing = false in onDestroy → if body skipped
        controller.destroy()
    }
}

@RunWith(RobolectricTestRunner::class)
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
