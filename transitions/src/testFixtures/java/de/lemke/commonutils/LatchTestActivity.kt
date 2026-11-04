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
import de.lemke.commonutils.ui.utils.SingleLaunchResultLauncher
import de.lemke.commonutils.ui.utils.registerForSingleLaunchResult

/** Activity whose `onResume`, `onNewIntent` and result launchers drive the launch latch in tests. */
public class LatchTestActivity : AppCompatActivity() {
    public var onResumeAction: () -> Unit = {}
    public var onResult: (ActivityResult) -> Unit = {}
    public var onOtherResult: (ActivityResult) -> Unit = {}
    public var onPermissionResult: (Boolean) -> Unit = {}
    public val launcher: SingleLaunchResultLauncher<Intent> = registerForSingleLaunchResult(StartActivityForResult()) { onResult(it) }
    public val otherLauncher: SingleLaunchResultLauncher<Intent> =
        registerForSingleLaunchResult(StartActivityForResult()) { onOtherResult(it) }
    public val permissionLauncher: SingleLaunchResultLauncher<String> =
        registerForSingleLaunchResult(RequestPermission()) { onPermissionResult(it) }

    override fun onResume() {
        super.onResume()
        onResumeAction()
    }

    /** Delivers [intent] the way API 29+ does for a singleTop activity: without a pause. */
    public fun deliverNewIntent(intent: Intent): Unit = onNewIntent(intent)
}

/** Fragment with a view whose `onResume`, `onDestroyView` and result launcher drive the launch latch in tests. */
public class LatchTestFragment : Fragment() {
    public var onResumeAction: () -> Unit = {}
    public var onDestroyViewAction: () -> Unit = {}
    public var onResult: (ActivityResult) -> Unit = {}
    public val launcher: SingleLaunchResultLauncher<Intent> = registerForSingleLaunchResult(StartActivityForResult()) { onResult(it) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = View(inflater.context)

    override fun onResume() {
        super.onResume()
        onResumeAction()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onDestroyViewAction()
    }
}
