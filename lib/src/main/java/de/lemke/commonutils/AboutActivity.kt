package de.lemke.commonutils

import android.content.Intent
import android.graphics.Color
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
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED
import com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE
import com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
import com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE
import com.google.android.play.core.install.model.UpdateAvailability.UPDATE_NOT_AVAILABLE
import de.lemke.commonutils.databinding.ActivityAboutBinding
import dev.oneuiproject.oneui.layout.AppInfoLayout.OnClickListener
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.Loading
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NoConnection
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NoUpdate
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.NotUpdatable
import dev.oneuiproject.oneui.layout.AppInfoLayout.Status.UpdateAvailable
import kotlinx.coroutines.launch
import dev.oneuiproject.oneui.design.R as designR

class AboutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutBinding
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var appUpdateInfo: AppUpdateInfo
    private lateinit var activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var clicks = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackPressAnimation(binding.root)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        binding.appInfoLayout.updateStatus = Loading
        setVersionText()
        setOptionalText()
        binding.appInfoLayout.setMainButtonClickListener(object : OnClickListener {
            override fun onUpdateClicked(v: View) {
                startUpdateFlow()
            }

            override fun onRetryClicked(v: View) {
                binding.appInfoLayout.updateStatus = Loading
                checkUpdate()
            }
        })
        binding.aboutButtonOpenInStore.setOnClickListener { openApp(packageName, false) }
        binding.aboutButtonOpenSourceLicenses.setOnClickListener { startActivity(Intent(this, OssLicensesMenuActivity::class.java)) }
        activityResultLauncher = registerForActivityResult(StartIntentSenderForResult()) { result ->
            when (result.resultCode) {
                // For immediate updates, you might not receive RESULT_OK because
                // the update should already be finished by the time control is given back to your app.
                RESULT_OK -> Log.d("InAppUpdate", "Update successful")
                RESULT_CANCELED -> Log.w("InAppUpdate", "Update canceled")
                RESULT_IN_APP_UPDATE_FAILED -> Log.e("InAppUpdate", "Update failed")
            }
        }
        checkUpdate()
    }

    private fun setVersionText() {
        val version: TextView = binding.appInfoLayout.findViewById(designR.id.app_info_version)
        lifecycleScope.launch { setVersionTextView(version) }
        version.setOnClickListener {
            clicks++
            if (clicks > 5) {
                clicks = 0
                lifecycleScope.launch {
                    devModeEnabled = !devModeEnabled
                    setVersionTextView(version)
                    onDevModeChanged(devModeEnabled)
                }
            }
        }
    }

    private fun setVersionTextView(textView: TextView) {
        if (appVersion.isNotBlank()) {
            textView.text = getString(designR.string.version_info, appVersion + if (devModeEnabled) " (dev)" else "")
        } else lifecycleScope.launch {
            textView.text = getString(designR.string.version_info, getAppVersion() + if (devModeEnabled) " (dev)" else "")
        }
    }

    private fun setOptionalText() {
        binding.appInfoLayout.addOptionalText("").apply {
            text = optionalText
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
            setLinkTextColor(getColor(R.color.primary_color_themed))
        }
    }

    // Checks that the update is not stalled during 'onResume()'.
    // However, you should execute this check at all entry points into the app.
    override fun onResume() {
        super.onResume()
        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    // If an in-app update is already running, resume the update.
                    startUpdateFlow()
                }
            }
    }

    private fun checkUpdate() {
        Log.i(TAG, "Checking for updates")
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (caps == null || !caps.hasCapability(NET_CAPABILITY_VALIDATED)) {
            binding.appInfoLayout.updateStatus = NoConnection
            return
        }

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
                this.appUpdateInfo = appUpdateInfo
                when {
                    appUpdateInfo.updateAvailability() == UPDATE_AVAILABLE -> binding.appInfoLayout.updateStatus = UpdateAvailable
                    appUpdateInfo.updateAvailability() == UPDATE_NOT_AVAILABLE -> binding.appInfoLayout.updateStatus = NoUpdate
                }
            }
            .addOnFailureListener { appUpdateInfo: Exception ->
                binding.appInfoLayout.updateStatus = NotUpdatable
                Log.w(TAG, appUpdateInfo.message.toString())
            }
    }

    private fun startUpdateFlow() {
        try {
            Log.i(TAG, "Starting update flow")
            appUpdateManager.startUpdateFlowForResult(appUpdateInfo, activityResultLauncher, AppUpdateOptions.newBuilder(IMMEDIATE).build())
        } catch (e: Exception) {
            binding.appInfoLayout.updateStatus = NotUpdatable
            e.printStackTrace()
        }
    }

    companion object {
        const val TAG = "AboutActivity"
        var appVersion = ""
        var getAppVersion = suspend { "" }
        var optionalText = SpannableString("")
        var devModeEnabled = false
        var onDevModeChanged: suspend (Boolean) -> Unit = {}
    }
}