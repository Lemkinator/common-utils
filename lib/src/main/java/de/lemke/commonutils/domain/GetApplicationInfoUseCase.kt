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

import android.content.pm.ApplicationInfo
import de.lemke.commonutils.data.PackageLookup
import javax.inject.Inject

/** Looks up a package's [ApplicationInfo] by package name through [packageLookup]; main-safe, as every [PackageLookup] is. */
class GetApplicationInfoUseCase @Inject constructor(
    private val packageLookup: PackageLookup,
) {
    /** Returns the [ApplicationInfo] of [packageName], or null if no such package is installed. */
    suspend operator fun invoke(packageName: String): ApplicationInfo? = packageLookup.applicationInfo(packageName)
}
