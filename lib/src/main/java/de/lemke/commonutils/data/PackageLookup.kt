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
package de.lemke.commonutils.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.picker.helper.SeslAppInfoDataHelper
import androidx.picker.model.AppData.GridAppDataBuilder
import androidx.picker.model.AppInfoData
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.commonutils.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Boundary to the installed-package queries of `PackageManager`, so use cases and ViewModels can run over a fake in
 * tests. Implementations are main-safe.
 *
 * An app binds this to [DefaultPackageLookup] in its own Hilt module, e.g.
 * `@Binds abstract fun bindPackageLookup(lookup: DefaultPackageLookup): PackageLookup`.
 */
interface PackageLookup {
    /** Returns the installed launcher apps ready for a `SeslAppPickerGridView`. */
    suspend fun installedApps(): List<AppInfoData>

    /** Returns the [ApplicationInfo] of [packageName], or null if no such package is installed. */
    suspend fun applicationInfo(packageName: String): ApplicationInfo?
}

/** [PackageLookup] over the app's `PackageManager`, querying it on [ioDispatcher]. */
class DefaultPackageLookup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PackageLookup {
    override suspend fun installedApps(): List<AppInfoData> =
        withContext(ioDispatcher) { SeslAppInfoDataHelper(context, GridAppDataBuilder::class.java).getPackages() }

    override suspend fun applicationInfo(packageName: String): ApplicationInfo? = withContext(ioDispatcher) { lookUp(packageName) }

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
