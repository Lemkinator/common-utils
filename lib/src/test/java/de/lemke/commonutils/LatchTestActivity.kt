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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import de.lemke.commonutils.ui.utils.registerForSingleLaunchResult

/** Activity whose `onResume`, `onNewIntent` and result launchers drive the launch latch in tests. */
class LatchTestActivity : AppCompatActivity() {
    var onResumeAction: () -> Unit = {}
    var onResult: (ActivityResult) -> Unit = {}
    var onOtherResult: (ActivityResult) -> Unit = {}
    var onPermissionResult: (Boolean) -> Unit = {}
    val launcher = registerForSingleLaunchResult(StartActivityForResult()) { onResult(it) }
    val otherLauncher = registerForSingleLaunchResult(StartActivityForResult()) { onOtherResult(it) }
    val permissionLauncher = registerForSingleLaunchResult(RequestPermission()) { onPermissionResult(it) }

    override fun onResume() {
        super.onResume()
        onResumeAction()
    }

    /** Delivers [intent] the way API 29+ does for a singleTop activity: without a pause. */
    fun deliverNewIntent(intent: Intent) = onNewIntent(intent)
}

/** Fragment with a view whose `onResume` and result launcher drive the launch latch in tests. */
class LatchTestFragment : Fragment() {
    var onResumeAction: () -> Unit = {}
    var onResult: (ActivityResult) -> Unit = {}
    val launcher = registerForSingleLaunchResult(StartActivityForResult()) { onResult(it) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = View(inflater.context)

    override fun onResume() {
        super.onResume()
        onResumeAction()
    }
}
