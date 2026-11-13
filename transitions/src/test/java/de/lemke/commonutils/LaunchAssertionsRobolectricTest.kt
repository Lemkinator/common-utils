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
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.android.material.transition.platform.MaterialContainerTransform.FADE_MODE_THROUGH
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TARGET = "de.lemke.commonutils.TargetActivity"

class TargetActivity : Activity()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaunchAssertionsRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private val plainOptions: Bundle = ActivityOptions.makeBasic().toBundle()

    private val sceneOptions: Bundle by lazy {
        val activity =
            Robolectric
                .buildActivity(Activity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        ActivityOptions.makeSceneTransitionAnimation(activity, View(activity), "shared").toBundle()
    }

    private fun launch(vararg flags: Int) =
        Intent().setClassName("de.lemke.commonutils", TARGET).also { intent -> flags.forEach(intent::addFlags) }

    private fun Intent.withTransform(
        name: String = "commonUtilsActivityTransitionName",
        duration: Long = 400L,
        fadeMode: Int = 2,
    ) = putExtra("commonUtilsTransitionNameKey", name)
        .putExtra("commonUtilsDurationKey", duration)
        .putExtra("commonUtilsFadeModeKey", fadeMode)

    @Test
    fun `a single-top launch with plain options is a pop-over`() {
        launch(Intent.FLAG_ACTIVITY_SINGLE_TOP).shouldBePopOverTo(TargetActivity::class.java, plainOptions)
    }

    @Test
    fun `a pop-over of another class fails and names both classes`() {
        val error =
            shouldThrow<AssertionError> {
                launch(Intent.FLAG_ACTIVITY_SINGLE_TOP).shouldBePopOverTo(Activity::class.java, plainOptions)
            }

        error.message shouldContain "<android.app.Activity>"
        error.message shouldContain "<$TARGET>"
    }

    @Test
    fun `a pop-over without the single-top flag fails`() {
        val error = shouldThrow<AssertionError> { launch().shouldBePopOverTo(TargetActivity::class.java, plainOptions) }

        error.message shouldContain "single-top"
    }

    @Test
    fun `a pop-over that carries a container transform fails`() {
        val error =
            shouldThrow<AssertionError> {
                launch(Intent.FLAG_ACTIVITY_SINGLE_TOP).withTransform().shouldBePopOverTo(TargetActivity::class.java, plainOptions)
            }

        error.message shouldContain "Expected no container transform but the intent carries"
    }

    @Test
    fun `a pop-over without options fails`() {
        val error =
            shouldThrow<AssertionError> {
                launch(Intent.FLAG_ACTIVITY_SINGLE_TOP).shouldBePopOverTo(TargetActivity::class.java, null)
            }

        error.message shouldContain "with plain activity options but options were null"
    }

    @Test
    fun `a pop-over with a scene transition fails`() {
        val error =
            shouldThrow<AssertionError> {
                launch(Intent.FLAG_ACTIVITY_SINGLE_TOP).shouldBePopOverTo(TargetActivity::class.java, sceneOptions)
            }

        error.message shouldContain "with plain activity options but options were"
        error.message shouldNotContain "options were null"
    }

    @Test
    fun `a transform launch with the default name passes`() {
        launch().withTransform().shouldBeContainerTransformTo(TargetActivity::class.java, sceneOptions)
    }

    @Test
    fun `a transform launch with an explicit name duration and fade mode passes`() {
        launch()
            .withTransform("shared", 123L, FADE_MODE_THROUGH)
            .shouldBeContainerTransformTo(TargetActivity::class.java, sceneOptions, "shared", 123L, FADE_MODE_THROUGH)
    }

    @Test
    fun `a transform launch of another class fails and names both classes`() {
        val error =
            shouldThrow<AssertionError> {
                launch().withTransform().shouldBeContainerTransformTo(Activity::class.java, sceneOptions)
            }

        error.message shouldContain "<android.app.Activity>"
        error.message shouldContain "<$TARGET>"
    }

    @Test
    fun `a transform launch with another name fails and names both`() {
        val error =
            shouldThrow<AssertionError> {
                launch().withTransform("other").shouldBeContainerTransformTo(TargetActivity::class.java, sceneOptions)
            }

        error.message shouldContain "<commonUtilsActivityTransitionName>"
        error.message shouldContain "<other>"
    }

    @Test
    fun `a transform launch with the single-top flag fails`() {
        val error =
            shouldThrow<AssertionError> {
                launch(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .withTransform()
                    .shouldBeContainerTransformTo(TargetActivity::class.java, sceneOptions)
            }

        error.message shouldContain "without a single-top flag"
    }

    @Test
    fun `a transform launch with plain options fails`() {
        val error =
            shouldThrow<AssertionError> {
                launch().withTransform().shouldBeContainerTransformTo(TargetActivity::class.java, plainOptions)
            }

        error.message shouldContain "with a scene transition but options were"
        error.message shouldNotContain "options were null"
    }

    @Test
    fun `a transform launch without options fails`() {
        val error =
            shouldThrow<AssertionError> {
                launch().withTransform().shouldBeContainerTransformTo(TargetActivity::class.java, null)
            }

        error.message shouldContain "with a scene transition but options were null"
    }
}
