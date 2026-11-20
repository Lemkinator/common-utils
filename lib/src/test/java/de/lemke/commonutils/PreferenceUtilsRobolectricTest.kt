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

import android.app.ActivityManager
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.utils.deleteAppDataAndExit
import de.lemke.commonutils.ui.utils.initCommonUtilsPreferences
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLog

class EmptyPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreferenceUtilsRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    private lateinit var fragment: Fragment
    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        activity =
            Robolectric
                .buildActivity(AppCompatActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        fragment = Fragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(fragment, "testFrag")
            .commitNow()
    }

    @Test
    fun `deleteAppDataAndExit with null args shows dialog`() {
        fragment.deleteAppDataAndExit()
        (ShadowDialog.getLatestDialog() as AlertDialog).isShowing.shouldBeTrue()
    }

    @Test
    fun `deleteAppDataAndExit with custom args shows dialog`() {
        fragment.deleteAppDataAndExit(title = "T", message = "M", cancel = "C", delete = "D")
        (ShadowDialog.getLatestDialog() as AlertDialog).isShowing.shouldBeTrue()
    }

    @Test
    fun `deleteAppDataAndExit twice shows one dialog`() {
        fragment.deleteAppDataAndExit()
        fragment.deleteAppDataAndExit()

        ShadowDialog.getShownDialogs().size shouldBe 1
    }

    @Test
    fun `deleteAppDataAndExit positive button click clears the application user data after the delay`() {
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        fragment.deleteAppDataAndExit()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(activityManager).isApplicationUserDataCleared.shouldBeFalse()

        shadowOf(Looper.getMainLooper()).idleFor(500.milliseconds.toJavaDuration())

        shadowOf(activityManager).isApplicationUserDataCleared.shouldBeTrue()
    }

    @Test
    fun `initCommonUtilsPreferences logs and skips the dark mode setup when the screen lacks its preferences`() {
        val themed =
            Robolectric
                .buildActivity(NoActionBarTestActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        val preferences = EmptyPreferencesFragment()
        themed.supportFragmentManager
            .beginTransaction()
            .add(preferences, "emptyPreferences")
            .commitNow()
        ShadowLog.clear()

        preferences.initCommonUtilsPreferences(SettingsRepository(freshTestPreferences()))

        ShadowLog
            .getLogsForTag("PreferenceUtils")
            .filter { it.type == Log.ERROR }
            .map { it.msg } shouldBe listOf("autoDarkModePref or darkModePref is null, skipping initialization")
    }
}
