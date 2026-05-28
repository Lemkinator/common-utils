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
package de.lemke.commonutils.ui.fragment

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.graphics.Color.TRANSPARENT
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialSharedAxis
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED
import com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE
import com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
import com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE
import com.google.android.play.core.install.model.UpdateAvailability.UPDATE_NOT_AVAILABLE
import de.lemke.commonutils.DrawerHost
import de.lemke.commonutils.R
import de.lemke.commonutils.autoCleared
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.databinding.FragmentAboutBinding
import de.lemke.commonutils.openApp
import dev.oneuiproject.oneui.ktx.onMultiClick
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.Loading
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NoConnection
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NoUpdate
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NotUpdatable
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.UpdateAvailable
import kotlinx.coroutines.launch
import dev.oneuiproject.oneui.design.R as designR

/** Pre-built About fragment that shows the app version, optional text, and an in-app update check. */
class CommonUtilsAboutFragment : TransitionFragmentSharedAxis(R.layout.fragment_about, MaterialSharedAxis.Y) {
    private val binding by autoCleared { FragmentAboutBinding.bind(requireView()) }
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var appUpdateInfo: AppUpdateInfo
    private lateinit var activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.appInfoLayout.toolbar.visibility = View.GONE
        binding.appInfoLayout.apply {
            updateStatus = Loading
            setMainButtonClickListener {
                if (updateStatus == NoConnection) {
                    updateStatus = Loading
                    checkUpdate()
                } else {
                    startUpdateFlow()
                }
            }
        }
        appUpdateManager = AppUpdateManagerFactory.create(requireContext())
        setVersionText()
        setOptionalText()
        binding.aboutButtonOpenInStore.setOnClickListener { openApp(requireContext().packageName, false) }
        binding.aboutButtonOpenSourceLicenses.setOnClickListener {
            findNavController().navigate(R.id.commonutils_libs_dest)
        }
        activityResultLauncher =
            registerForActivityResult(StartIntentSenderForResult()) { result ->
                when (result.resultCode) {
                    RESULT_OK -> Log.d(TAG, "Update successful")
                    RESULT_CANCELED -> Log.w(TAG, "Update canceled")
                    RESULT_IN_APP_UPDATE_FAILED -> Log.e(TAG, "Update failed")
                }
            }
        checkUpdate()
    }

    override fun onDestroyView() {
        // TODO Remove both workarounds below once AppInfoLayout.onDetachedFromWindow() is fixed
        //  in oneui-design to clear its toolbar listener and restore the host action bar.
        //  Fix tracked in oneui-design fix/memory-leaks branch.

        // Workaround 1: AppInfoLayout sets mOnMenuItemClickListener on its toolbar (the lambda
        // captures AppInfoLayout itself). The toolbar stays alive in AppCompatDelegateImpl.mActionBar
        // after navigation, preventing GC of AppInfoLayout.
        binding.appInfoLayout.toolbar.setOnMenuItemClickListener(null)

        // Workaround 2: AppInfoLayout.init calls setSupportActionBar, replacing mActionBar with a
        // ToolbarActionBar wrapping the AppInfoLayout toolbar. Restore the NavDrawerLayout toolbar
        // so mActionBar no longer retains the detached AppInfoLayout view hierarchy via
        // SemToolbar.mParent references.
        (requireActivity() as? DrawerHost)?.drawerLayout?.toolbar?.let {
            (requireActivity() as AppCompatActivity).setSupportActionBar(it)
        }
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    startUpdateFlow()
                }
            }
    }

    private fun setVersionText() {
        val version: TextView = binding.appInfoLayout.findViewById(designR.id.app_info_version)
        viewLifecycleOwner.lifecycleScope.launch { setVersionTextView(version) }
        version.onMultiClick {
            commonUtilsSettings.devModeEnabled = !commonUtilsSettings.devModeEnabled
            viewLifecycleOwner.lifecycleScope.launch { setVersionTextView(version) }
        }
    }

    private suspend fun setVersionTextView(textView: TextView) {
        appVersion.ifBlank { getAppVersion() }.let { ver ->
            textView.text =
                getString(
                    designR.string.oui_des_version_info,
                    ver + if (commonUtilsSettings.devModeEnabled) " (dev)" else "",
                )
        }
    }

    private fun setOptionalText() {
        binding.appInfoLayout.addOptionalText("").apply {
            text = optionalText ?: getString(R.string.commonutils_app_description)
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = TRANSPARENT
            setLinkTextColor(requireContext().getColor(R.color.primary_color_themed))
        }
    }

    private fun checkUpdate() {
        Log.i(TAG, "Checking for updates")
        val connectivityManager = requireContext().getSystemService(ConnectivityManager::class.java)
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (caps == null || !caps.hasCapability(NET_CAPABILITY_VALIDATED)) {
            binding.appInfoLayout.updateStatus = NoConnection
            return
        }
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info: AppUpdateInfo ->
                appUpdateInfo = info
                when {
                    info.updateAvailability() == UPDATE_AVAILABLE -> binding.appInfoLayout.updateStatus = UpdateAvailable
                    info.updateAvailability() == UPDATE_NOT_AVAILABLE -> binding.appInfoLayout.updateStatus = NoUpdate
                }
            }.addOnFailureListener { e: Exception ->
                binding.appInfoLayout.updateStatus = NotUpdatable
                Log.w(TAG, e.message.toString())
            }
    }

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
        private const val TAG = "CommonUtilsAboutFragment"

        /** Static version string displayed in the about screen; takes precedence if non-empty. */
        var appVersion = ""

        /** Suspend function used to resolve the version string at display time; used when [appVersion] is empty. */
        var getAppVersion = suspend { "" }

        /** Optional extra text shown below the version; rendered as a [SpannableString] for clickable spans. */
        var optionalText: SpannableString? = null
    }
}
