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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.R
import de.lemke.commonutils.backup.BackupScreenDeps
import de.lemke.commonutils.backup.BackupViewModel
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.databinding.ActivitySettingsCommonUtilsBinding
import de.lemke.commonutils.ui.utils.addShareAppAndRateRelativeLinksCard
import de.lemke.commonutils.ui.utils.bindBackupPreferences
import de.lemke.commonutils.ui.utils.initCommonUtilsPreferences
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import java.util.Optional
import javax.inject.Inject

/** Pre-built settings screen backed by a [PreferenceFragmentCompat] with common-utils defaults. */
@AndroidEntryPoint
public class CommonUtilsSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsCommonUtilsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsCommonUtilsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(R.id.settingsLayout, SettingsFragment()).commit()
    }

    public companion object {
        /** Preference XML resource IDs inflated in order; configure via [setupCommonUtilsSettingsActivity]. */
        public var preferences: List<Int> =
            listOf(
                R.xml.preferences_design,
                R.xml.preferences_general_language,
                R.xml.preferences_dev_options_delete_app_data,
                R.xml.preferences_more_info,
            )

        /** Whether the share and rate card is added; configure via [setupCommonUtilsSettingsActivity]. */
        public var relativeLinksCard: Boolean = true
    }

    /** [PreferenceFragmentCompat] that inflates the configured preference XML resources. */
    @AndroidEntryPoint
    public class SettingsFragment : PreferenceFragmentCompat() {
        @Inject
        public lateinit var settings: SettingsRepository

        @Inject
        public lateinit var backupScreen: Optional<BackupScreenDeps>

        override fun onCreatePreferences(
            bundle: Bundle?,
            str: String?,
        ) {
            preferenceManager.preferenceDataStore = settings.preferenceDataStore()
            preferences.forEach { addPreferencesFromResource(it) }
            if (!backupScreen.isPresent) {
                findPreference<PreferenceCategory>(
                    getString(R.string.commonutils_preference_key_backup),
                )?.let { preferenceScreen.removePreference(it) }
            }
        }

        override fun onCreate(bundle: Bundle?) {
            super.onCreate(bundle)
            initCommonUtilsPreferences(settings)
            backupScreen.orElse(null)?.let { screen ->
                val factory = viewModelFactory { initializer { BackupViewModel(screen.engine, screen.documents, screen.dispatcher) } }
                val viewModel = ViewModelProvider(this, factory)[BackupViewModel::class.java]
                bindBackupPreferences(screen.sectionLabels) { viewModel.backup }
            }
        }

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)
            if (relativeLinksCard && preferenceScreen != null) addShareAppAndRateRelativeLinksCard()
        }
    }
}
