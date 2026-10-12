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

import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.utils.setupCommonUtilsAboutActivity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class InstrumentedWaitsTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val fakeSettings: SettingsRepository = SettingsRepository(freshTestPreferences())

    @Before
    fun setUp() {
        hiltRule.inject()
        setupCommonUtilsAboutActivity("1.0.0")
    }

    @After
    fun tearDown() {
        CommonUtilsAboutActivity.appVersion = ""
    }

    @Test
    fun waitUntil_returnsOnceTheConditionHoldsOnTheActivity() {
        ActivityScenario.launch(CommonUtilsAboutActivity::class.java).use { scenario ->
            scenario.waitUntil { lifecycle.currentState == Lifecycle.State.RESUMED }
        }
    }

    @Test
    fun waitUntil_throwsOnceTheTimeoutHasPassed() {
        ActivityScenario.launch(CommonUtilsAboutActivity::class.java).use { scenario ->
            val error =
                shouldThrow<IllegalStateException> {
                    scenario.waitUntil(timeout = 30.milliseconds, poll = 5.milliseconds) { false }
                }

            error.message shouldBe "condition not met within 30ms"
        }
    }

    @Test
    fun isSettled_isTrueForALaidOutListAndFalseWhileItSmoothScrolls() {
        ActivityScenario.launch(CommonUtilsAboutActivity::class.java).use { scenario ->
            val settled =
                scenario.read { activity ->
                    val list =
                        RecyclerView(activity)
                            .apply {
                                layoutManager = LinearLayoutManager(activity)
                                adapter = StubAdapter()
                            }.laidOut()
                    val beforeScroll = list.isSettled
                    list.smoothScrollToPosition(SCROLL_TARGET)
                    beforeScroll to list.isSettled
                }

            settled shouldBe (true to false)
        }
    }

    private fun <V : View> V.laidOut(): V {
        val exactly = MeasureSpec.makeMeasureSpec(LAYOUT_SIZE, MeasureSpec.EXACTLY)
        measure(exactly, exactly)
        layout(0, 0, LAYOUT_SIZE, LAYOUT_SIZE)
        return this
    }

    private class StubAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): RecyclerView.ViewHolder = object : RecyclerView.ViewHolder(View(parent.context)) {}

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int,
        ) = Unit

        override fun getItemCount() = ITEMS
    }

    private companion object {
        const val LAYOUT_SIZE = 100
        const val ITEMS = 10
        const val SCROLL_TARGET = 5
    }
}
