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

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import de.lemke.commonutils.data.SettingsRepository
import dev.oneuiproject.oneui.ktx.hideSoftInput
import dev.oneuiproject.oneui.layout.ToolbarLayout
import kotlinx.coroutines.flow.MutableStateFlow

/** Returns a [ToolbarLayout.SearchModeListener] that updates [search] on query changes and persists the last query. */
fun AppCompatActivity.getCommonUtilsSearchListener(
    settings: SettingsRepository,
    search: MutableStateFlow<String?>,
    @StringRes queryHint: Int? = null,
) = object : ToolbarLayout.SearchModeListener {
    override fun onQueryTextSubmit(query: String?): Boolean = setSearch(query).also { hideSoftInput() }

    override fun onQueryTextChange(query: String?): Boolean = setSearch(query)

    private fun setSearch(query: String?): Boolean {
        if (search.value == null) return false
        search.value = query ?: ""
        settings.search = query ?: ""
        return true
    }

    override fun onSearchModeToggle(
        searchView: SearchView,
        isActive: Boolean,
    ) {
        if (isActive) {
            search.value = settings.search
            queryHint?.let { searchView.queryHint = getString(it) }
            searchView.setQuery(search.value, false)
        } else {
            search.value = null
        }
    }
}
