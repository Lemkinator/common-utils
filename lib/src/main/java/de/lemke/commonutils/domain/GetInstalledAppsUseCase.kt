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

import androidx.picker.model.AppInfoData
import de.lemke.commonutils.data.PackageLookup
import javax.inject.Inject

/**
 * Returns installed apps ready for a `SeslAppPickerGridView`, read through [packageLookup].
 * Callers rendering through `AppPickerStrategy` get the sub-label shaping applied there, not here.
 *
 * [invoke] is main-safe, as every [PackageLookup] is.
 */
class GetInstalledAppsUseCase @Inject constructor(
    private val packageLookup: PackageLookup,
) {
    suspend operator fun invoke(): List<AppInfoData> = packageLookup.installedApps()
}
