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
package de.lemke.commonutils.ui.activity

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import de.lemke.commonutils.R
import de.lemke.commonutils.addShareAppAndRateRelativeLinksCard
import de.lemke.commonutils.databinding.ActivitySettingsCommonUtilsBinding
import de.lemke.commonutils.initCommonUtilsPreferences
import de.lemke.commonutils.prepareActivityTransformationTo
import de.lemke.commonutils.setCustomBackAnimation
import kotlinx.coroutines.launch

class CommonUtilsSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsCommonUtilsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsCommonUtilsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(R.id.settingsLayout, SettingsFragment()).commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(
            bundle: Bundle?,
            str: String?,
        ) {
            preferences.forEach { addPreferencesFromResource(it) }
        }

        override fun onCreate(bundle: Bundle?) {
            super.onCreate(bundle)
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
    }

    companion object {
        var preferences =
            listOf(
                R.xml.preferences_design,
                R.xml.preferences_general_language,
                R.xml.preferences_dev_options_delete_app_data,
                R.xml.preferences_more_info,
            )
        var initPreferences: suspend PreferenceFragmentCompat.() -> Unit = {}
    }
}
