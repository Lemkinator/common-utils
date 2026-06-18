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

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.content.DialogInterface.BUTTON_POSITIVE
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.oneuiproject.oneui.ktx.setOnClickListenerWithProgress
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.oneuiproject.oneui.design.R as designR

private const val DELETE_APP_DATA_DELAY_MS = 500L

/** Shows a confirmation dialog and clears all application user data on confirmation. */
@NoCoverage
fun Fragment.deleteAppDataAndExit(
    title: String? = null,
    message: String? = null,
    cancel: String? = null,
    delete: String? = null,
) {
    val dialog =
        AlertDialog
            .Builder(requireContext())
            .setTitle(title ?: getString(R.string.commonutils_delete_appdata_and_exit))
            .setMessage(message ?: getString(R.string.commonutils_delete_appdata_and_exit_warning))
            .setNegativeButton(cancel ?: getString(designR.string.oui_des_common_cancel), null)
            .setPositiveButton(delete ?: getString(R.string.commonutils_delete), null)
            .create()
    dialog.show()
    dialog.getButton(BUTTON_POSITIVE).apply {
        setTextColor(requireContext().getColor(designR.color.oui_des_functional_red_color))
        setOnClickListenerWithProgress { _, _ ->
            lifecycleScope.launch {
                delay(DELETE_APP_DATA_DELAY_MS.milliseconds)
                (context?.getSystemService(ACTIVITY_SERVICE) as? ActivityManager)?.clearApplicationUserData()
            }
        }
    }
}
