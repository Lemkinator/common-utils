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

import android.content.pm.ApplicationInfo
import androidx.picker.model.AppInfoData
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [PackageLookup] that serves [installedAppsResult] and [applicationInfos] and records every call.
 *
 * Both calls record themselves first, then await [gate] when set, so a test can observe a loading state and
 * complete the gate to let the calls return.
 */
class FakePackageLookup(
    vararg applicationInfos: ApplicationInfo,
) : PackageLookup {
    /** Result of [installedApps]: its list on success, its exception thrown on failure. */
    var installedAppsResult: Result<List<AppInfoData>> = Result.success(emptyList())

    /** Installed packages by package name; [applicationInfo] returns null for every other name. */
    val applicationInfos: MutableMap<String, ApplicationInfo> = applicationInfos.associateByTo(mutableMapOf()) { it.packageName }

    /** When set, every call suspends until this completes. */
    var gate: CompletableDeferred<Unit>? = null

    /** Number of [installedApps] calls so far. */
    var installedAppsCalls: Int = 0
        private set

    private val lookedUp = mutableListOf<String>()

    /** Package names passed to [applicationInfo], in call order. */
    val lookedUpPackages: List<String> get() = lookedUp.toList()

    override suspend fun installedApps(): List<AppInfoData> {
        installedAppsCalls++
        gate?.await()
        return installedAppsResult.getOrThrow()
    }

    override suspend fun applicationInfo(packageName: String): ApplicationInfo? {
        lookedUp += packageName
        gate?.await()
        return applicationInfos[packageName]
    }
}
