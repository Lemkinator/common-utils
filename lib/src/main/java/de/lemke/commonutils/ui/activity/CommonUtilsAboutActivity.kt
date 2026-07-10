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

import android.graphics.Color.TRANSPARENT
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED
import com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE
import com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
import com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE
import com.google.android.play.core.install.model.UpdateAvailability.UPDATE_NOT_AVAILABLE
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.NoCoverage
import de.lemke.commonutils.R
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.databinding.ActivityAboutBinding
import de.lemke.commonutils.ui.utils.openApp
import de.lemke.commonutils.ui.utils.prepareActivityTransformationBetween
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.transformToActivity
import dev.oneuiproject.oneui.ktx.onMultiClick
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.Loading
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NoConnection
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NoUpdate
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NotUpdatable
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.UpdateAvailable
import javax.inject.Inject
import kotlinx.coroutines.launch
import dev.oneuiproject.oneui.design.R as designR

/** Pre-built About screen that shows the app version, optional text, and an in-app update check. */
@AndroidEntryPoint
class CommonUtilsAboutActivity : AppCompatActivity() {
    @Inject
    lateinit var settings: SettingsRepository
    private lateinit var binding: ActivityAboutBinding
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var appUpdateInfo: AppUpdateInfo
    private lateinit var activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationBetween()
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        binding.appInfoLayout.apply {
            updateStatus = Loading
            setMainButtonClickListener { onMainButtonClicked() }
        }
        appUpdateManager = AppUpdateManagerFactory.create(this)
        setVersionText()
        setOptionalText()
        binding.aboutButtonOpenInStore.setOnClickListener { openApp(packageName, false) }
        binding.aboutButtonOpenSourceLicenses.apply {
            setOnClickListener { transformToActivity(CommonUtilsLibsActivity::class.java, transitionName = "CommonUtilsLibsTransition") }
        }
        activityResultLauncher = registerForActivityResult(StartIntentSenderForResult(), ::onUpdateActivityResult)
        checkUpdate()
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener(::onResumeUpdateCheck)
    }

    /** Handles the main button click: retries update check when offline, or starts the update flow. */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun onMainButtonClicked() {
        if (binding.appInfoLayout.updateStatus == NoConnection) {
            binding.appInfoLayout.updateStatus = Loading
            checkUpdate()
        } else {
            startUpdateFlow()
        }
    }

    /**
     * Handles the activity result from the in-app update flow.
     *
     * For immediate updates [RESULT_OK] may never arrive because the update finishes
     * before control is returned to the app.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun onUpdateFlowResult(resultCode: Int) {
        when (resultCode) {
            RESULT_OK -> Log.d(TAG, "Update successful")
            RESULT_CANCELED -> Log.w(TAG, "Update canceled")
            RESULT_IN_APP_UPDATE_FAILED -> Log.e(TAG, "Update failed")
        }
    }

    @NoCoverage
    private fun onUpdateActivityResult(result: ActivityResult) = onUpdateFlowResult(result.resultCode)

    private fun setVersionText() {
        val version: TextView = binding.appInfoLayout.findViewById(designR.id.app_info_version)
        setVersionTextView(version)
        version.onMultiClick {
            settings.devModeEnabled = !settings.devModeEnabled
            setVersionTextView(version)
        }
    }

    private fun setVersionTextView(textView: TextView) {
        lifecycleScope.launch {
            appVersion.ifBlank { getAppVersion() }.let { appVersion ->
                textView.text =
                    getString(designR.string.oui_des_version_info, appVersion + if (settings.devModeEnabled) " (dev)" else "")
            }
        }
    }

    private fun setOptionalText() {
        binding.appInfoLayout.addOptionalText("").apply {
            text = optionalText ?: getString(R.string.commonutils_app_description)
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = TRANSPARENT
            setLinkTextColor(getColor(R.color.primary_color_themed))
        }
    }

    @NoCoverage
    private fun checkUpdate() {
        Log.i(TAG, "Checking for updates")
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (caps == null || !caps.hasCapability(NET_CAPABILITY_VALIDATED)) {
            binding.appInfoLayout.updateStatus = NoConnection
            return
        }

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener(::onUpdateAvailabilityFetched)
            .addOnFailureListener(::onUpdateFetchFailed)
    }

    // Play Core callbacks: require a live Google Play Store connection, untestable in JVM tests.
    // The generated thin-wrapper lambda classes are excluded by Kover class-name patterns in build.gradle.kts.

    @NoCoverage
    private fun onUpdateAvailabilityFetched(info: AppUpdateInfo) {
        appUpdateInfo = info
        when {
            info.updateAvailability() == UPDATE_AVAILABLE -> binding.appInfoLayout.updateStatus = UpdateAvailable
            info.updateAvailability() == UPDATE_NOT_AVAILABLE -> binding.appInfoLayout.updateStatus = NoUpdate
        }
    }

    @NoCoverage
    private fun onUpdateFetchFailed(exception: Exception) {
        binding.appInfoLayout.updateStatus = NotUpdatable
        Log.w(TAG, exception.message.toString())
    }

    @NoCoverage
    private fun onResumeUpdateCheck(info: AppUpdateInfo) {
        if (info.updateAvailability() == DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
            startUpdateFlow()
        }
    }

    // startUpdateFlow calls Play Core's startUpdateFlowForResult, which requires the live Play Store.
    @NoCoverage
    @Suppress("TooGenericExceptionCaught")
    private fun startUpdateFlow() {
        try {
            Log.i(TAG, "Starting update flow")
            appUpdateManager.startUpdateFlowForResult(appUpdateInfo, activityResultLauncher, AppUpdateOptions.newBuilder(IMMEDIATE).build())
        } catch (e: Exception) {
            binding.appInfoLayout.updateStatus = NotUpdatable
            Log.e(TAG, "Update flow failed", e)
        }
    }

    companion object {
        private const val TAG = "CommonUtilsAboutActivity"

        /** Static version string displayed in the about screen; takes precedence if non-empty. */
        var appVersion = ""

        /** Suspend function used to resolve the version string at display time; used when [appVersion] is empty. */
        var getAppVersion = suspend { "" }

        /** Optional extra text shown below the version; rendered as a [SpannableString] for clickable spans. */
        var optionalText: SpannableString? = null
    }
}
