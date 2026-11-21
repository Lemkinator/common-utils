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

import android.os.Parcel
import de.lemke.commonutils.ui.utils.OnboardingContext
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingContextParcelRobolectricTest {
    @Test
    fun `an onboarding context survives a parcel round trip`() {
        val context =
            OnboardingContext(
                mainActivityName = "de.lemke.app.MainActivity",
                steps = listOf("de.lemke.app.StepOne", "de.lemke.app.StepTwo"),
                versionCode = 12,
                versionName = "1.2.0",
                lastVersionCode = 11,
                lastVersionName = "1.1.0",
                tosChanged = true,
            )
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(context, 0)
            parcel.setDataPosition(0)

            parcel.readParcelable(OnboardingContext::class.java.classLoader, OnboardingContext::class.java) shouldBe context
        } finally {
            parcel.recycle()
        }
    }
}
