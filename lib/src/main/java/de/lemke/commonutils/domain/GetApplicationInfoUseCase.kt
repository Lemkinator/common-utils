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
package de.lemke.commonutils.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.commonutils.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Looks up a package's [ApplicationInfo] by package name on [ioDispatcher], so callers may invoke it from the main thread. */
class GetApplicationInfoUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** Returns the [ApplicationInfo] of [packageName], or null if no such package is installed. */
    suspend operator fun invoke(packageName: String): ApplicationInfo? = withContext(ioDispatcher) { lookUp(packageName) }

    private fun lookUp(packageName: String): ApplicationInfo? =
        try {
            if (SDK_INT >= TIRAMISU) {
                context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
        } catch (_: NameNotFoundException) {
            null
        }
}
