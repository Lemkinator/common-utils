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

import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import de.lemke.commonutils.ui.utils.deleteAppDataAndExit
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36])
class PreferenceUtilsRobolectricTest {
    private lateinit var fragment: Fragment

    @BeforeEach
    fun setUp() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
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
    fun `deleteAppDataAndExit positive button click launches delete coroutine`() {
        fragment.deleteAppDataAndExit()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
