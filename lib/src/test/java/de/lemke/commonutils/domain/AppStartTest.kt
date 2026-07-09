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
package de.lemke.commonutils.domain

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain

class AppStartTest : ShouldSpec() {
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

    init {
        context("isFirstTime") {
            should("be true when lastVersionCode is -1") {
                appStart(lastVersionCode = -1).isFirstTime.shouldBeTrue()
            }
            should("be false when lastVersionCode is not -1") {
                appStart(lastVersionCode = 5).isFirstTime.shouldBeFalse()
            }
        }
        context("isFirstTimeVersion") {
            should("be true when lastVersionCode less than versionCode") {
                appStart(versionCode = 10, lastVersionCode = 9).isFirstTimeVersion.shouldBeTrue()
            }
            should("be false on first install (lastVersionCode == -1)") {
                appStart(lastVersionCode = -1).isFirstTimeVersion.shouldBeFalse()
            }
            should("be false when codes are equal") {
                appStart(versionCode = 10, lastVersionCode = 10).isFirstTimeVersion.shouldBeFalse()
            }
            should("be false when lastVersionCode greater") {
                appStart(versionCode = 9, lastVersionCode = 10).isFirstTimeVersion.shouldBeFalse()
            }
        }
        context("tosAccepted") {
            should("be true when acceptedTosVersion equals tosVersion") {
                appStart(tosVersion = 2, acceptedTosVersion = 2).tosAccepted.shouldBeTrue()
            }
            should("be true when acceptedTosVersion exceeds tosVersion") {
                appStart(tosVersion = 1, acceptedTosVersion = 3).tosAccepted.shouldBeTrue()
            }
            should("be false when acceptedTosVersion below tosVersion") {
                appStart(tosVersion = 2, acceptedTosVersion = 1).tosAccepted.shouldBeFalse()
            }
        }
        context("shouldShowOOBE") {
            should("be true on first install") {
                appStart(lastVersionCode = -1, tosVersion = 1, acceptedTosVersion = 1).shouldShowOOBE.shouldBeTrue()
            }
            should("be true when TOS not accepted") {
                appStart(lastVersionCode = 5, tosVersion = 2, acceptedTosVersion = 1).shouldShowOOBE.shouldBeTrue()
            }
            should("be false when returning user with accepted TOS") {
                appStart(lastVersionCode = 5, tosVersion = 1, acceptedTosVersion = 1).shouldShowOOBE.shouldBeFalse()
            }
        }
        context("versionThresholdPassed") {
            should("be false on first install (lastVersionCode == -1)") {
                appStart(versionCode = 10, lastVersionCode = -1).versionThresholdPassed(5).shouldBeFalse()
            }
            should("be true when threshold within upgrade range") {
                appStart(versionCode = 10, lastVersionCode = 5).versionThresholdPassed(7).shouldBeTrue()
            }
            should("be false when threshold below range") {
                appStart(versionCode = 10, lastVersionCode = 5).versionThresholdPassed(4).shouldBeFalse()
            }
            should("be true when threshold equals lastVersionCode (inclusive start)") {
                appStart(versionCode = 10, lastVersionCode = 5).versionThresholdPassed(5).shouldBeTrue()
            }
            should("be false when threshold equals versionCode (exclusive end)") {
                appStart(versionCode = 10, lastVersionCode = 5).versionThresholdPassed(10).shouldBeFalse()
            }
            should("be false when threshold above range") {
                appStart(versionCode = 10, lastVersionCode = 5).versionThresholdPassed(11).shouldBeFalse()
            }
        }
        context("toString") {
            should("include versionCode") {
                appStart(versionCode = 42).toString() shouldContain "42"
            }
            should("include versionName") {
                appStart(versionName = "2.3.4").toString() shouldContain "2.3.4"
            }
        }
    }
}
