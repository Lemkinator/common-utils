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
package de.lemke.commonutils.ui.widget

import android.content.Context
import androidx.annotation.Keep
import androidx.picker.controller.strategy.AppItemStrategy
import androidx.picker.di.AppPickerContext
import androidx.picker.helper.SeslAppInfoDataHelper
import androidx.picker.model.AppData
import androidx.picker.model.AppData.GridAppDataBuilder
import androidx.picker.model.AppInfoData
import androidx.picker.model.viewdata.AppInfoViewData
import androidx.picker.model.viewdata.ViewData

/**
 * [AppItemStrategy] for `SeslAppPickerGridView` (`app:strategy="de.lemke.commonutils.ui.widget.AppPickerStrategy"`
 * in layout XML) that makes every app entry searchable by both its label and package name, so users can
 * find an app whether they remember its display name or its package identifier.
 */
@Keep
class AppPickerStrategy(
    appPickerContext: AppPickerContext,
) : AppItemStrategy(appPickerContext) {
    override fun convert(
        dataList: List<AppData>,
        comparator: Comparator<ViewData>?,
    ) = super.convert(dataList, comparator).also { results ->
        results.filterIsInstance<AppInfoViewData>().forEach { it.searchable = listOfNotNull(it.label, it.packageName) }
    }
}

/** Returns installed apps for a `SeslAppPickerGridView`, with [AppInfoData.subLabel] set to the package name. */
fun Context.getInstalledAppsForPicker(): List<AppInfoData> =
    SeslAppInfoDataHelper(this, GridAppDataBuilder::class.java).getPackages().onEach { it.subLabel = it.packageName }
