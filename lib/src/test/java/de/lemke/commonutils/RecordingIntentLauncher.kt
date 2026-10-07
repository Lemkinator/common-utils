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
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.app.ActivityOptionsCompat
import de.lemke.commonutils.ui.utils.SingleLaunchResultLauncher

/** Records every launched [Intent] and reports [admits], or throws [failure] on launch instead. */
class RecordingIntentLauncher(
    private val failure: RuntimeException? = null,
    private val admits: Boolean = true,
) : SingleLaunchResultLauncher<Intent>() {
    val launched = mutableListOf<Intent>()

    override val contract = StartActivityForResult()

    override fun tryLaunch(
        input: Intent,
        options: ActivityOptionsCompat?,
    ): Boolean {
        failure?.let { throw it }
        if (admits) launched += input
        return admits
    }

    override fun unregister() = Unit
}
