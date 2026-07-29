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

import de.lemke.commonutils.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins every common-utils preference XML against [SettingsRepository]: every persisting widget's `android:key`
 * matches a property, every such key declares an `android:defaultValue`, and that default agrees with the
 * property's own delegate default on an empty store. See [assertPreferenceXmlBoundToSettings].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreferenceXmlParityTest {
    @Test
    fun `preferences_design is bound to SettingsRepository`() {
        assertPreferenceXmlBoundToSettings(R.xml.preferences_design, ::SettingsRepository)
    }

    @Test
    fun `preferences_general_language is bound to SettingsRepository`() {
        assertPreferenceXmlBoundToSettings(R.xml.preferences_general_language, ::SettingsRepository)
    }

    @Test
    fun `preferences_general_language_and_image_save_location is bound to SettingsRepository`() {
        assertPreferenceXmlBoundToSettings(R.xml.preferences_general_language_and_image_save_location, ::SettingsRepository)
    }

    @Test
    fun `preferences_dev_options_delete_app_data is bound to SettingsRepository`() {
        assertPreferenceXmlBoundToSettings(R.xml.preferences_dev_options_delete_app_data, ::SettingsRepository)
    }

    @Test
    fun `preferences_more_info is bound to SettingsRepository`() {
        assertPreferenceXmlBoundToSettings(R.xml.preferences_more_info, ::SettingsRepository)
    }
}
