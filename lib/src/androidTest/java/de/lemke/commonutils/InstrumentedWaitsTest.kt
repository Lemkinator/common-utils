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

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.utils.setupCommonUtilsAboutActivity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class InstrumentedWaitsTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @get:Rule(order = 1)
    val timeout: Timeout = Timeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

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
                    scenario.waitUntil(timeout = 30.milliseconds, poll = 50.milliseconds) { false }
                }

            error.message shouldBe "condition not met within 30ms"
        }
    }

    @Test
    fun waitUntil_throwsWithinTheTimeoutWhileTheMainThreadStaysBusy() {
        val busy = BusyMainThread()
        ActivityScenario.launch(CommonUtilsAboutActivity::class.java).use { scenario ->
            val start = TimeSource.Monotonic.markNow()
            val error =
                try {
                    busy.start()
                    shouldThrow<IllegalStateException> {
                        scenario.waitUntil(timeout = BUSY_TIMEOUT, poll = 5.milliseconds) { true }
                    }
                } finally {
                    busy.stop()
                }

            error.message shouldBe "main thread not idle within 200ms"
            start.elapsedNow() shouldBeLessThan BUSY_TIMEOUT + BUSY_MARGIN
        }
    }

    @Test
    fun waitUntil_rethrowsTheExceptionOfTheConditionUnchanged() {
        ActivityScenario.launch(CommonUtilsAboutActivity::class.java).use { scenario ->
            val error = shouldThrow<UnsupportedOperationException> { scenario.waitUntil { throw UnsupportedOperationException("boom") } }

            error.message shouldBe "boom"
        }
    }

    @Test
    fun waitUntil_failsAtOnceWhenCalledOnTheMainThread() {
        ActivityScenario.launch(CommonUtilsAboutActivity::class.java).use { scenario ->
            var thrown: Throwable? = null

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                thrown = runCatching { scenario.waitUntil { true } }.exceptionOrNull()
            }

            (thrown is RuntimeException) shouldBe true
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

    private class BusyMainThread {
        private val handler = Handler(Looper.getMainLooper())
        private val running = AtomicBoolean(true)
        private var giveUp = TimeSource.Monotonic.markNow()
        private val spin =
            object : Runnable {
                override fun run() {
                    val end = TimeSource.Monotonic.markNow() + SPIN
                    while (!end.hasPassedNow()) Thread.onSpinWait()
                    if (running.get() && !giveUp.hasPassedNow()) handler.post(this)
                }
            }

        fun start() {
            giveUp = TimeSource.Monotonic.markNow() + TEST_TIMEOUT_SECONDS.seconds
            handler.post(spin)
        }

        fun stop() {
            running.set(false)
            handler.removeCallbacks(spin)
        }
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
        val BUSY_TIMEOUT = 200.milliseconds
        val BUSY_MARGIN = 2.seconds
        val SPIN = 5.milliseconds
        const val TEST_TIMEOUT_SECONDS = 30L
        const val LAYOUT_SIZE = 100
        const val ITEMS = 10
        const val SCROLL_TARGET = 5
    }
}
