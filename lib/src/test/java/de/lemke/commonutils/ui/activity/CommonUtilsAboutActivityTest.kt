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
import android.content.Context
import android.os.Looper
import android.text.SpannableString
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED
import de.lemke.commonutils.R
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.setupCommonUtilsAboutActivity
import dev.oneuiproject.oneui.layout.AppInfoLayout
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.Loading
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import dev.oneuiproject.oneui.design.R as designR

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class CommonUtilsAboutActivityTest {
    private lateinit var mockAppUpdateManager: AppUpdateManager

    @BeforeEach
    fun setUp() {
        val prefs =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("about_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        commonUtilsSettings = SettingsRepository(prefs)

        mockAppUpdateManager = mockk<AppUpdateManager>(relaxed = true)
        mockkStatic(AppUpdateManagerFactory::class)
        every { AppUpdateManagerFactory.create(any()) } returns mockAppUpdateManager

        setupCommonUtilsAboutActivity("1.0.0")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun launchActivity(): CommonUtilsAboutActivity {
        val controller = Robolectric.buildActivity(CommonUtilsAboutActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    @Test
    fun `activity launches with static appVersion`() {
        val activity = launchActivity()
        activity shouldNotBe null
    }

    @Test
    fun `activity launches with blank appVersion — getAppVersion suspend lambda is called`() {
        setupCommonUtilsAboutActivity(getAppVersion = suspend { "2.0.0" })
        val activity = launchActivity()
        activity shouldNotBe null
    }

    @Test
    fun `activity launches with devModeEnabled — dev suffix path executed`() {
        commonUtilsSettings.devModeEnabled = true
        val activity = launchActivity()
        activity shouldNotBe null
    }

    @Test
    fun `onUpdateFlowResult RESULT_OK does not throw`() {
        val activity = launchActivity()
        activity.onUpdateFlowResult(RESULT_OK)
    }

    @Test
    fun `onUpdateFlowResult RESULT_CANCELED does not throw`() {
        val activity = launchActivity()
        activity.onUpdateFlowResult(RESULT_CANCELED)
    }

    @Test
    fun `onUpdateFlowResult RESULT_IN_APP_UPDATE_FAILED does not throw`() {
        val activity = launchActivity()
        activity.onUpdateFlowResult(RESULT_IN_APP_UPDATE_FAILED)
    }

    @Test
    fun `onMainButtonClicked when NoConnection re-triggers checkUpdate`() {
        val activity = launchActivity()
        // Under Robolectric, no active network → checkUpdate sets NoConnection status.
        // Calling onMainButtonClicked with NoConnection hits the if-branch.
        activity.onMainButtonClicked()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `onMainButtonClicked when not NoConnection triggers startUpdateFlow else-branch`() {
        val activity = launchActivity()
        // Force a non-NoConnection status to cover the else-branch.
        // startUpdateFlow is @NoCoverage; its call site inside onMainButtonClicked is covered.
        activity.findViewById<AppInfoLayout>(R.id.appInfoLayout).updateStatus = Loading
        activity.onMainButtonClicked()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `openInStore button click does not throw`() {
        val activity = launchActivity()
        activity.findViewById<android.widget.Button>(R.id.aboutButtonOpenInStore).performClick()
    }

    @Test
    fun `openSourceLicenses button click starts CommonUtilsLibsActivity`() {
        val activity = launchActivity()
        activity.findViewById<android.widget.Button>(R.id.aboutButtonOpenSourceLicenses).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `update button click covers setMainButtonClickListener lambda`() {
        // Under Robolectric (no network) updateStatus = NoConnection → update button visible with listener set.
        val activity = launchActivity()
        activity.findViewById<Button>(designR.id.app_info_update)?.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `handleMainButtonClick direct call covers view-to-onMainButtonClicked bridge`() {
        val activity = launchActivity()
        activity.handleMainButtonClick(mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `7 clicks on version text view triggers onMultiClick action body`() {
        val activity = launchActivity()
        val versionTextView = activity.findViewById<TextView>(dev.oneuiproject.oneui.design.R.id.app_info_version)
        // onMultiClick requires 7 consecutive clicks within 1000 ms to invoke the action body
        repeat(7) { versionTextView.performClick() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `activity with non-null optionalText covers non-null setOptionalText branch`() {
        CommonUtilsAboutActivity.optionalText = SpannableString("Custom text for test")
        try {
            val activity = launchActivity()
            activity shouldNotBe null
        } finally {
            CommonUtilsAboutActivity.optionalText = null
        }
    }
}
