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
package de.lemke.commonutils.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import de.lemke.commonutils.R
import de.lemke.commonutils.addShareAppAndRateRelativeLinksCard
import de.lemke.commonutils.clearLastNestedScrollingChild
import de.lemke.commonutils.initCommonUtilsPreferences
import kotlinx.coroutines.launch

/** Pre-built settings fragment backed by [PreferenceFragmentCompat] with common-utils defaults. */
class CommonUtilsSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        preferences.forEach { addPreferencesFromResource(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initCommonUtilsPreferences()
        lifecycleScope.launch { initPreferences() }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        addShareAppAndRateRelativeLinksCard()
    }

    override fun onDestroyView() {
        listView.stopNestedScroll()
        clearLastNestedScrollingChild()
        super.onDestroyView()
    }

    companion object {
        /** Preference XML resource IDs inflated in order. */
        var preferences =
            listOf(
                R.xml.preferences_design,
                R.xml.preferences_general_language,
                R.xml.preferences_dev_options_delete_app_data,
                R.xml.preferences_more_info,
            )

        /** Optional suspend block run after preference inflation for app-specific init. */
        var initPreferences: suspend PreferenceFragmentCompat.() -> Unit = {}
    }
}
