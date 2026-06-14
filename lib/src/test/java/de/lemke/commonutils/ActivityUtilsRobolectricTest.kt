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
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class ActivityUtilsRobolectricTest {
    @Test
    fun `setupCommonUtilsSettingsActivity vararg sets preferences list`() {
        setupCommonUtilsSettingsActivity(1, 2, 3)
        CommonUtilsSettingsActivity.preferences shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `setupCommonUtilsSettingsActivity list sets preferences list`() {
        setupCommonUtilsSettingsActivity(listOf(10, 20))
        CommonUtilsSettingsActivity.preferences shouldBe listOf(10, 20)
    }

    @Test
    fun `setupCommonUtilsAboutActivity string version sets appVersion`() {
        setupCommonUtilsAboutActivity("1.2.3")
        CommonUtilsAboutActivity.appVersion shouldBe "1.2.3"
        CommonUtilsAboutActivity.optionalText shouldBe null
    }

    @Test
    fun `setupCommonUtilsAboutMeActivity sets onShareApp callback`() {
        var called = false
        setupCommonUtilsAboutMeActivity(onShareApp = { called = true })
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        CommonUtilsAboutMeActivity.onShareApp.invoke(activity)
        called shouldBe true
    }

    @Test
    fun `setupCommonUtilsAboutActivity suspend version sets getAppVersion`() {
        val getVersion: suspend () -> String = { "2.0.0" }
        setupCommonUtilsAboutActivity(getVersion)
        CommonUtilsAboutActivity.getAppVersion shouldNotBe null
        CommonUtilsAboutActivity.optionalText.shouldBeNull()
    }
}
