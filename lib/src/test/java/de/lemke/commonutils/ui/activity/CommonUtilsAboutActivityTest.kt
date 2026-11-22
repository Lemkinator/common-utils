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
package de.lemke.commonutils.ui.activity

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.os.Looper
import android.text.SpannableString
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.reflect.DeviceInfo
import androidx.reflect.SeslBaseReflector
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.R
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.shouldBePopOverFromPopOverTo
import de.lemke.commonutils.ui.utils.setupCommonUtilsAboutActivity
import dev.oneuiproject.oneui.layout.AppInfoLayout
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.Loading
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NoConnection
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NoUpdate
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NotUpdatable
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.UpdateAvailable
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowNetworkCapabilities
import dev.oneuiproject.oneui.design.R as designR

/**
 * JUnit4 (not JUnit5/Kotest like the rest of this module): `HiltAndroidRule`/`@HiltAndroidTest`
 * require a JUnit4 `@RunWith(RobolectricTestRunner::class)` runner, so this class runs under the
 * `junit-vintage-engine` island — see `lib/build.gradle.kts` test dependencies.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class CommonUtilsAboutActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val fakeSettings: SettingsRepository = SettingsRepository(freshTestPreferences())

    private lateinit var mockAppUpdateManager: AppUpdateManager

    @Before
    fun setUp() {
        hiltRule.inject()
        mockAppUpdateManager = mockk<AppUpdateManager>(relaxed = true)
        mockkStatic(AppUpdateManagerFactory::class)
        every { AppUpdateManagerFactory.create(any()) } returns mockAppUpdateManager

        setupCommonUtilsAboutActivity("1.0.0")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun launchActivity(block: (CommonUtilsAboutActivity) -> Unit) {
        ActivityScenario.launch(CommonUtilsAboutActivity::class.java).use { it.onActivity(block) }
    }

    @Test
    fun `activity launches with static appVersion`() {
        launchActivity { activity -> activity shouldNotBe null }
    }

    @Test
    fun `activity launches with devModeEnabled - dev suffix path executed`() {
        fakeSettings.devModeEnabled = true
        launchActivity { activity -> activity shouldNotBe null }
    }

    @Test
    fun `an update flow result of RESULT_OK is logged as successful`() {
        deliverUpdateResult(RESULT_OK)

        updateLogs() shouldBe listOf(Log.DEBUG to "Update successful")
    }

    @Test
    fun `an update flow result of RESULT_CANCELED is logged as canceled`() {
        deliverUpdateResult(RESULT_CANCELED)

        updateLogs() shouldBe listOf(Log.WARN to "Update canceled")
    }

    @Test
    fun `an update flow result of RESULT_IN_APP_UPDATE_FAILED is logged as failed`() {
        deliverUpdateResult(RESULT_IN_APP_UPDATE_FAILED)

        updateLogs() shouldBe listOf(Log.ERROR to "Update failed")
    }

    @Test
    fun `an update check without any active network shows NoConnection`() {
        val connectivity = ApplicationProvider.getApplicationContext<Context>().getSystemService(ConnectivityManager::class.java)
        shadowOf(connectivity).setActiveNetworkInfo(null)

        launchActivity { activity -> activity.updateStatus() shouldBe NoConnection }
    }

    @Test
    fun `update button click without a connection checks again once the network is validated`() {
        launchActivity { activity ->
            val appInfo = activity.findViewById<AppInfoLayout>(R.id.appInfoLayout)
            appInfo.updateStatus shouldBe NoConnection
            verify(exactly = 1) { mockAppUpdateManager.appUpdateInfo }
            val connectivity = activity.getSystemService(ConnectivityManager::class.java)
            val validated = ShadowNetworkCapabilities.newInstance()
            shadowOf(validated).addCapability(NET_CAPABILITY_VALIDATED)
            shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, validated)

            activity.findViewById<Button>(designR.id.app_info_update).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            appInfo.updateStatus shouldBe Loading
            verify(exactly = 2) { mockAppUpdateManager.appUpdateInfo }
        }
    }

    @Test
    fun `update button click while loading fails the update flow and shows NotUpdatable`() {
        launchActivity { activity ->
            val appInfo = activity.findViewById<AppInfoLayout>(R.id.appInfoLayout)
            appInfo.updateStatus = Loading

            activity.findViewById<Button>(designR.id.app_info_update).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            appInfo.updateStatus shouldBe NotUpdatable
        }
    }

    @Test
    fun `openInStore button click does not throw`() {
        launchActivity { activity -> activity.findViewById<Button>(R.id.aboutButtonOpenInStore).performClick() }
    }

    @Test
    fun `openSourceLicenses button click starts CommonUtilsLibsActivity`() {
        launchActivity { activity ->
            activity.findViewById<Button>(R.id.aboutButtonOpenSourceLicenses).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(activity).nextStartedActivity.component?.className shouldBe
                CommonUtilsLibsActivity::class.java.name
        }
    }

    @Test
    fun `openSourceLicenses button click inside a pop-over starts CommonUtilsLibsActivity as a pop-over page`() {
        val semIsPopOver = Configuration::class.java.getMethod("toString")
        launchActivity { activity ->
            mockkStatic(DeviceInfo::class)
            every { DeviceInfo.isOneUI() } returns true
            mockkStatic(SeslBaseReflector::class)
            every { SeslBaseReflector.getDeclaredMethod(Configuration::class.java, "semIsPopOver") } returns semIsPopOver
            every { SeslBaseReflector.invoke(any(), semIsPopOver) } returns true

            activity.findViewById<Button>(R.id.aboutButtonOpenSourceLicenses).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            val started = shadowOf(activity).nextStartedActivityForResult
            started.intent.shouldBePopOverFromPopOverTo(CommonUtilsLibsActivity::class.java, started.options)
        }
    }

    @Test
    fun `7 clicks on version text view triggers onMultiClick action body`() {
        launchActivity { activity ->
            val versionTextView = activity.findViewById<TextView>(designR.id.app_info_version)
            // onMultiClick requires 7 consecutive clicks within 1000 ms to invoke the action body
            repeat(7) { versionTextView.performClick() }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `activity with non-null optionalText covers non-null setOptionalText branch`() {
        CommonUtilsAboutActivity.optionalText = SpannableString("Custom text for test")
        try {
            launchActivity { activity -> activity shouldNotBe null }
        } finally {
            CommonUtilsAboutActivity.optionalText = null
        }
    }

    @Test
    fun `validated network with an available update shows UpdateAvailable`() {
        validateNetwork()
        every { mockAppUpdateManager.appUpdateInfo } returns Tasks.forResult(updateInfo(UpdateAvailability.UPDATE_AVAILABLE))

        launchActivity { activity -> activity.updateStatus() shouldBe UpdateAvailable }
    }

    @Test
    fun `validated network without an update shows NoUpdate`() {
        validateNetwork()
        every { mockAppUpdateManager.appUpdateInfo } returns Tasks.forResult(updateInfo(UpdateAvailability.UPDATE_NOT_AVAILABLE))

        launchActivity { activity -> activity.updateStatus() shouldBe NoUpdate }
    }

    @Test
    fun `validated network with unknown availability keeps Loading`() {
        validateNetwork()
        every { mockAppUpdateManager.appUpdateInfo } returns Tasks.forResult(updateInfo(UpdateAvailability.UNKNOWN))

        launchActivity { activity -> activity.updateStatus() shouldBe Loading }
    }

    @Test
    fun `failed update check shows NotUpdatable`() {
        validateNetwork()
        every { mockAppUpdateManager.appUpdateInfo } returns Tasks.forException(IllegalStateException("offline"))

        launchActivity { activity -> activity.updateStatus() shouldBe NotUpdatable }
    }

    @Test
    fun `resuming during a developer triggered update restarts the update flow once`() {
        validateNetwork()
        val info = updateInfo(UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
        every { mockAppUpdateManager.appUpdateInfo } returns Tasks.forResult(info)

        launchActivity { }

        verify(exactly = 1) {
            mockAppUpdateManager.startUpdateFlowForResult(
                info,
                any<ActivityResultLauncher<IntentSenderRequest>>(),
                any<AppUpdateOptions>(),
            )
        }
    }

    private fun deliverUpdateResult(resultCode: Int) {
        validateNetwork()
        every { mockAppUpdateManager.appUpdateInfo } returns
            Tasks.forResult(updateInfo(UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS))
        val launcher = slot<ActivityResultLauncher<IntentSenderRequest>>()
        every { mockAppUpdateManager.startUpdateFlowForResult(any(), capture(launcher), any<AppUpdateOptions>()) } returns true

        launchActivity { activity ->
            val sender = PendingIntent.getActivity(activity, 0, Intent(), PendingIntent.FLAG_IMMUTABLE).intentSender
            launcher.captured.launch(IntentSenderRequest.Builder(sender).build())
            ShadowLog.clear()
            val requestCode = shadowOf(activity).lastIntentSenderRequest.requestCode
            activity.activityResultRegistry.dispatchResult(requestCode, resultCode, Intent())
        }
    }

    private fun updateLogs() = ShadowLog.getLogsForTag("CommonUtilsAboutActivity").map { it.type to it.msg }

    private fun CommonUtilsAboutActivity.updateStatus() = findViewById<AppInfoLayout>(R.id.appInfoLayout).updateStatus

    private fun updateInfo(availability: Int): AppUpdateInfo = mockk { every { updateAvailability() } returns availability }

    private fun validateNetwork() {
        val connectivity = ApplicationProvider.getApplicationContext<Context>().getSystemService(ConnectivityManager::class.java)
        val validated = ShadowNetworkCapabilities.newInstance()
        shadowOf(validated).addCapability(NET_CAPABILITY_VALIDATED)
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, validated)
    }
}
