@file:Suppress("unused")

package de.lemke.commonutils

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import de.lemke.commonutils.data.commonUtilsSettings
import dev.oneuiproject.oneui.ktx.hideSoftInput
import dev.oneuiproject.oneui.layout.ToolbarLayout
import kotlinx.coroutines.flow.MutableStateFlow

private const val TAG = "SearchUtils"

fun AppCompatActivity.getCommonUtilsSearchListener(search: MutableStateFlow<String?>, @StringRes queryHint: Int? = null) =
    object : ToolbarLayout.SearchModeListener {
        override fun onQueryTextSubmit(query: String?): Boolean = setSearch(query).also { hideSoftInput() }
        override fun onQueryTextChange(query: String?): Boolean = setSearch(query)
        private fun setSearch(query: String?): Boolean {
            if (search.value == null) return false
            search.value = query ?: ""
            commonUtilsSettings.search = query ?: ""
            return true
        }

        override fun onSearchModeToggle(searchView: SearchView, isActive: Boolean) {
            if (isActive) {
                search.value = commonUtilsSettings.search
                queryHint?.let { searchView.queryHint = getString(it) }
                searchView.setQuery(search.value, false)
            } else search.value = null
        }
    }
