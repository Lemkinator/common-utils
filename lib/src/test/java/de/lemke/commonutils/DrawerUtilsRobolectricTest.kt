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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.gms.tasks.Tasks
import com.google.android.material.navigation.NavigationView
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.utils.COMMONUTILS_KEY_IS_ACTION_MODE
import de.lemke.commonutils.ui.utils.COMMONUTILS_KEY_IS_SEARCH_MODE
import de.lemke.commonutils.ui.utils.COMMONUTILS_KEY_SELECTED_IDS
import de.lemke.commonutils.ui.utils.onSingleLaunchItemSelected
import de.lemke.commonutils.ui.utils.restoreSearchAndActionMode
import de.lemke.commonutils.ui.utils.saveSearchAndActionMode
import de.lemke.commonutils.ui.utils.setupHeaderAndNavRail
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import dev.oneuiproject.oneui.navigation.widget.DrawerNavigationView
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import dev.oneuiproject.oneui.design.R as designR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DrawerUtilsRobolectricTest {
    @get:Rule
    val destroyActivities = DestroyActivitiesRule()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `saveSearchAndActionMode sets search mode key`() {
        val bundle = Bundle()
        bundle.saveSearchAndActionMode(isSearchMode = true)
        bundle.getBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE).shouldBeTrue()
        bundle.getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE).shouldBeFalse()
    }

    @Test
    fun `saveSearchAndActionMode sets action mode key and selected ids`() {
        val bundle = Bundle()
        bundle.saveSearchAndActionMode(isActionMode = true, selectedIds = setOf(1L, 2L, 3L))
        bundle.getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE).shouldBeTrue()
        bundle.getLongArray(COMMONUTILS_KEY_SELECTED_IDS)?.toSet() shouldBe setOf(1L, 2L, 3L)
    }

    @Test
    fun `saveSearchAndActionMode with defaults sets no keys`() {
        val bundle = Bundle()
        bundle.saveSearchAndActionMode()
        bundle.getBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE).shouldBeFalse()
        bundle.getBoolean(COMMONUTILS_KEY_IS_ACTION_MODE).shouldBeFalse()
    }

    @Test
    fun `restoreSearchAndActionMode calls bundleIsNull when null`() {
        var called = false
        null.restoreSearchAndActionMode(bundleIsNull = { called = true })
        called.shouldBeTrue()
    }

    @Test
    fun `restoreSearchAndActionMode calls onSearchMode when search mode set`() {
        val bundle = Bundle().apply { saveSearchAndActionMode(isSearchMode = true) }
        var searchCalled = false
        bundle.restoreSearchAndActionMode(onSearchMode = { searchCalled = true })
        searchCalled.shouldBeTrue()
    }

    @Test
    fun `restoreSearchAndActionMode calls onActionMode with correct ids`() {
        val ids = setOf(10L, 20L)
        val bundle = Bundle().apply { saveSearchAndActionMode(isActionMode = true, selectedIds = ids) }
        var restoredIds: Set<Long>? = null
        bundle.restoreSearchAndActionMode(onActionMode = { restoredIds = it })
        restoredIds shouldBe ids
    }

    @Test
    fun `restoreSearchAndActionMode calls neither callback for empty bundle`() {
        var searchCalled = false
        var actionCalled = false
        Bundle().restoreSearchAndActionMode(
            onSearchMode = { searchCalled = true },
            onActionMode = { actionCalled = true },
        )
        searchCalled.shouldBeFalse()
        actionCalled.shouldBeFalse()
    }

    @Test
    fun `onSingleLaunchItemSelected runs the first selection and drops a fast repeat`() {
        val navView = navigationView()
        val listenerSlot = slot<NavigationView.OnNavigationItemSelectedListener>()
        every { navView.setNavigationItemSelectedListener(capture(listenerSlot)) } answers { }
        val item = mockk<MenuItem>()
        val selected = mutableListOf<MenuItem>()
        navView.onSingleLaunchItemSelected {
            selected += it
            navView.context.singleLaunchActivity(Intent(navView.context, Activity::class.java))
        }

        listenerSlot.captured.onNavigationItemSelected(item).shouldBeTrue()
        listenerSlot.captured.onNavigationItemSelected(item).shouldBeFalse()

        selected shouldBe listOf(item)
    }

    @Test
    fun `onSingleLaunchItemSelected returns the listener result`() {
        val navView = navigationView()
        val listenerSlot = slot<NavigationView.OnNavigationItemSelectedListener>()
        every { navView.setNavigationItemSelectedListener(capture(listenerSlot)) } answers { }
        navView.onSingleLaunchItemSelected { false }

        listenerSlot.captured.onNavigationItemSelected(mockk()).shouldBeFalse()
    }

    @Test
    fun `setupHeaderAndNavRail makes the header button open the about screen`() {
        stubUpdateAvailability(UpdateAvailability.UPDATE_NOT_AVAILABLE)
        val activity = themedActivity()
        val drawer = NavDrawerLayout(activity)
        activity.setContentView(drawer)

        drawer.setupHeaderAndNavRail("About app")
        drawer.findViewById<View>(designR.id.oui_des_drawer_header_button).performClick()

        shadowOf(activity).nextStartedActivity.component?.className shouldBe CommonUtilsAboutActivity::class.java.name
    }

    @Test
    fun `setupHeaderAndNavRail badges the header button when an update is available`() {
        stubUpdateAvailability(UpdateAvailability.UPDATE_AVAILABLE)
        val activity = themedActivity()
        val drawer = NavDrawerLayout(activity)
        activity.setContentView(drawer)
        val badge = drawer.findViewById<View>(designR.id.oui_des_drawer_header_button_badge)

        drawer.setupHeaderAndNavRail("About app")
        shadowOf(Looper.getMainLooper()).idle()

        badge.isVisible.shouldBeTrue()
    }

    private fun themedActivity() =
        Robolectric
            .buildActivity(NoActionBarTestActivity::class.java)
            .setup()
            .track(destroyActivities)
            .get()

    private fun stubUpdateAvailability(availability: Int) {
        val info = mockk<AppUpdateInfo> { every { updateAvailability() } returns availability }
        val manager = mockk<AppUpdateManager> { every { appUpdateInfo } returns Tasks.forResult(info) }
        mockkStatic(AppUpdateManagerFactory::class)
        every { AppUpdateManagerFactory.create(any()) } returns manager
    }

    private fun navigationView(): DrawerNavigationView {
        val activity =
            Robolectric
                .buildActivity(AppCompatActivity::class.java)
                .setup()
                .track(destroyActivities)
                .get()
        return mockk { every { context } returns activity }
    }
}
