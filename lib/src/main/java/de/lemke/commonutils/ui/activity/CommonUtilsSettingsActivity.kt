package de.lemke.commonutils.ui.activity

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import de.lemke.commonutils.R
import de.lemke.commonutils.addShareAppAndRateRelativeLinksCard
import de.lemke.commonutils.databinding.ActivitySettingsCommonUtilsBinding
import de.lemke.commonutils.initCommonUtilsPreferences
import de.lemke.commonutils.prepareActivityTransformationTo
import de.lemke.commonutils.setCustomBackAnimation

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
        override fun onCreatePreferences(bundle: Bundle?, str: String?) {
            preferences.forEach { addPreferencesFromResource(it) }
        }

        override fun onCreate(bundle: Bundle?) {
            super.onCreate(bundle)
            initCommonUtilsPreferences()
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            addShareAppAndRateRelativeLinksCard()
        }
    }

    companion object {
        var preferences = listOf(
            R.xml.preferences_design,
            R.xml.preferences_general_language,
            R.xml.preferences_dev_options_delete_app_data,
            R.xml.preferences_more_info
        )
    }
}