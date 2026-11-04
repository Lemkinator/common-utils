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

import de.lemke.commonutils.ui.utils.admitsLaunchPreparation
import de.lemke.commonutils.ui.utils.singleLaunch
import de.lemke.commonutils.ui.utils.singleLaunchSuspending
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdmitsLaunchPreparationRobolectricTest : LaunchLatchRobolectricTest() {
    @Test
    fun `a context without a live activity admits the preparation`() {
        runBlocking { RuntimeEnvironment.getApplication().admitsLaunchPreparation() }.shouldBeTrue()
    }

    @Test
    fun `an activity without a latch admits the preparation`() {
        val activity = resumed().get()

        runBlocking { activity.admitsLaunchPreparation() }.shouldBeTrue()
    }

    @Test
    fun `an idle resumed activity admits the preparation`() {
        val activity = resumed().get()
        activity.singleLaunch {}.shouldBeTrue()

        runBlocking { activity.admitsLaunchPreparation() }.shouldBeTrue()
    }

    @Test
    fun `a leaving activity does not admit the preparation`() {
        val activity = resumed().get()
        activity.launchScreen().shouldBeTrue()

        runBlocking { activity.admitsLaunchPreparation() }.shouldBeFalse()
    }

    @Test
    fun `a leaving activity with a held input admits the preparation only as the work of that input`() {
        val activity = resumed().get()
        val asWork = mutableListOf<Boolean>()
        val release = activity.startBusyAndCollect(asWork)

        runBlocking { activity.admitsLaunchPreparation() }.shouldBeTrue()
        activity.launchScreen().shouldBeTrue()
        runBlocking { activity.admitsLaunchPreparation() }.shouldBeFalse()
        release.complete(Unit)
        shadowLooper.idle()

        asWork shouldBe listOf(true)
    }

    private fun LatchTestActivity.startBusyAndCollect(results: MutableList<Boolean>): CompletableDeferred<Unit> {
        val release = CompletableDeferred<Unit>()
        singleLaunchSuspending(
            work = {
                release.await()
                results += admitsLaunchPreparation()
            },
            then = {},
        ).shouldBeTrue()
        return release
    }
}
