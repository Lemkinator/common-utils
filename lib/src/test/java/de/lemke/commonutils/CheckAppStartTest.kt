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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckAppStartTest {
    private fun appStart(
        versionCode: Int = 10,
        versionName: String = "1.0",
        lastVersionCode: Int = 9,
        lastVersionName: String = "0.9",
        tosVersion: Int = 1,
        acceptedTosVersion: Int = 1,
        result: AppStartResult =
            when {
                lastVersionCode == -1 -> AppStartResult.FIRST_TIME
                lastVersionCode < versionCode -> AppStartResult.FIRST_TIME_VERSION
                else -> AppStartResult.NORMAL
            },
    ) = AppStart(result, versionCode, versionName, lastVersionCode, lastVersionName, tosVersion, acceptedTosVersion)

    // region isFirstTime

    @Test
    fun `isFirstTime is true when lastVersionCode is -1`() {
        assertTrue(appStart(lastVersionCode = -1).isFirstTime)
    }

    @Test
    fun `isFirstTime is false when lastVersionCode is not -1`() {
        assertFalse(appStart(lastVersionCode = 5).isFirstTime)
    }

    // endregion

    // region isFirstTimeVersion

    @Test
    fun `isFirstTimeVersion is true when lastVersionCode less than versionCode`() {
        assertTrue(appStart(versionCode = 10, lastVersionCode = 9).isFirstTimeVersion)
    }

    @Test
    fun `isFirstTimeVersion is false when codes are equal`() {
        assertFalse(appStart(versionCode = 10, lastVersionCode = 10).isFirstTimeVersion)
    }

    @Test
    fun `isFirstTimeVersion is false when lastVersionCode greater`() {
        assertFalse(appStart(versionCode = 9, lastVersionCode = 10).isFirstTimeVersion)
    }

    // endregion

    // region tosAccepted

    @Test
    fun `tosAccepted is true when acceptedTosVersion equals tosVersion`() {
        assertTrue(appStart(tosVersion = 2, acceptedTosVersion = 2).tosAccepted)
    }

    @Test
    fun `tosAccepted is true when acceptedTosVersion exceeds tosVersion`() {
        assertTrue(appStart(tosVersion = 1, acceptedTosVersion = 3).tosAccepted)
    }

    @Test
    fun `tosAccepted is false when acceptedTosVersion below tosVersion`() {
        assertFalse(appStart(tosVersion = 2, acceptedTosVersion = 1).tosAccepted)
    }

    // endregion

    // region shouldShowOOBE

    @Test
    fun `shouldShowOOBE is true on first install`() {
        assertTrue(appStart(lastVersionCode = -1, tosVersion = 1, acceptedTosVersion = 1).shouldShowOOBE)
    }

    @Test
    fun `shouldShowOOBE is true when TOS not accepted`() {
        assertTrue(appStart(lastVersionCode = 5, tosVersion = 2, acceptedTosVersion = 1).shouldShowOOBE)
    }

    @Test
    fun `shouldShowOOBE is false when returning user with accepted TOS`() {
        assertFalse(appStart(lastVersionCode = 5, tosVersion = 1, acceptedTosVersion = 1).shouldShowOOBE)
    }

    // endregion

    // region versionThresholdPassed

    @Test
    fun `versionThresholdPassed is true when threshold within upgrade range`() {
        // lastVersionCode=5, versionCode=10 → range 5..<10
        assertTrue(appStart(versionCode = 10, lastVersionCode = 5).versionThresholdPassed(7))
    }

    @Test
    fun `versionThresholdPassed is false when threshold below range`() {
        assertFalse(appStart(versionCode = 10, lastVersionCode = 5).versionThresholdPassed(4))
    }

    @Test
    fun `versionThresholdPassed is true when threshold equals lastVersionCode (inclusive start)`() {
        assertTrue(appStart(versionCode = 10, lastVersionCode = 5).versionThresholdPassed(5))
    }

    @Test
    fun `versionThresholdPassed is false when threshold equals versionCode (exclusive end)`() {
        assertFalse(appStart(versionCode = 10, lastVersionCode = 5).versionThresholdPassed(10))
    }

    @Test
    fun `versionThresholdPassed is false when threshold above range`() {
        assertFalse(appStart(versionCode = 10, lastVersionCode = 5).versionThresholdPassed(11))
    }

    // endregion

    // region toString

    @Test
    fun `toString includes versionCode`() {
        val s = appStart(versionCode = 42).toString()
        assertTrue(s.contains("42"))
    }

    @Test
    fun `toString includes versionName`() {
        val s = appStart(versionName = "2.3.4").toString()
        assertTrue(s.contains("2.3.4"))
    }

    // endregion
}
