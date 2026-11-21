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

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
import com.google.android.play.core.install.model.UpdateAvailability.UNKNOWN
import de.lemke.commonutils.ui.utils.onAppUpdateAvailable
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateManagerUtilsRobolectricTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `an available update runs the action`() {
        checkUpdate(FakeAppUpdateManager(ctx).apply { setUpdateAvailable(2) }) shouldBe true
    }

    @Test
    fun `an update in progress does not run the action`() {
        checkUpdate(DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) shouldBe false
    }

    @Test
    fun `no update does not run the action`() {
        checkUpdate(FakeAppUpdateManager(ctx).apply { setUpdateNotAvailable() }) shouldBe false
    }

    @Test
    fun `unknown availability does not run the action`() {
        checkUpdate(UNKNOWN) shouldBe false
    }

    @Test
    fun `a failed check does not run the action`() {
        stubManager(managerWith(Tasks.forException(IllegalStateException("offline"))))
        var called = false

        ctx.onAppUpdateAvailable { called = true }
        shadowOf(Looper.getMainLooper()).idle()

        called shouldBe false
    }

    private fun checkUpdate(availability: Int): Boolean {
        val info = mockk<AppUpdateInfo> { every { updateAvailability() } returns availability }
        return checkUpdate(managerWith(Tasks.forResult(info)))
    }

    private fun checkUpdate(manager: AppUpdateManager): Boolean {
        stubManager(manager)
        var called = false

        ctx.onAppUpdateAvailable { called = true }
        shadowOf(Looper.getMainLooper()).idle()

        return called
    }

    private fun managerWith(task: Task<AppUpdateInfo>) = mockk<AppUpdateManager> { every { appUpdateInfo } returns task }

    private fun stubManager(manager: AppUpdateManager) {
        mockkStatic(AppUpdateManagerFactory::class)
        every { AppUpdateManagerFactory.create(any()) } returns manager
    }
}
