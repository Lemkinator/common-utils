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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InstrumentedWaitsRobolectricTest {
    @Test
    fun `waitUntil returns once the condition holds on the activity`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            var polls = 0

            scenario.waitUntil { ++polls >= 3 }

            polls shouldBe 3
        }
    }

    @Test
    fun `waitUntil waits for a change made on another thread`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            val done = AtomicBoolean(false)
            thread {
                Thread.sleep(50)
                done.set(true)
            }

            scenario.waitUntil { done.get() }
        }
    }

    @Test
    fun `waitUntil rethrows the exception of the condition unchanged`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            val error = shouldThrow<UnsupportedOperationException> { scenario.waitUntil { throw UnsupportedOperationException("boom") } }

            error.message shouldBe "boom"
        }
    }

    @Test
    fun `waitUntil checks once at timeout zero`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            scenario.waitUntil(timeout = Duration.ZERO) { true }
            val error = shouldThrow<IllegalStateException> { scenario.waitUntil(timeout = Duration.ZERO) { false } }

            error.message shouldBe "condition not met within 0s"
        }
    }

    @Test
    fun `waitUntil throws once the timeout has passed`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            val error =
                shouldThrow<IllegalStateException> {
                    scenario.waitUntil(timeout = 30.milliseconds, poll = 5.milliseconds) { false }
                }

            error.message shouldBe "condition not met within 30ms"
        }
    }

    @Test
    fun `a laid out recycler view without a layout manager is settled`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            scenario.read { activity -> RecyclerView(activity).laidOut().isSettled } shouldBe true
        }
    }

    @Test
    fun `a recycler view with a pending layout request is not settled`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            val settled =
                scenario.read { activity ->
                    val list = RecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }.laidOut()
                    val beforeRequest = list.isSettled
                    list.requestLayout()
                    beforeRequest to list.isSettled
                }

            settled shouldBe (true to false)
        }
    }

    @Test
    fun `a recycler view that smooth scrolls is not settled`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            val settled =
                scenario.read { activity ->
                    val list =
                        RecyclerView(activity)
                            .apply {
                                layoutManager = LinearLayoutManager(activity)
                                adapter = StubAdapter()
                            }.laidOut()
                    list.smoothScrollToPosition(SCROLL_TARGET)
                    list.isSettled
                }

            settled shouldBe false
        }
    }

    @Test
    fun `a recycler view in a scroll animation that is no smooth scroll is not settled`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            val state =
                scenario.read { activity ->
                    val list =
                        RecyclerView(activity)
                            .apply {
                                layoutManager = LinearLayoutManager(activity)
                                adapter = StubAdapter()
                            }.laidOut()
                    val beforeScroll = list.isSettled
                    list.smoothScrollBy(0, SCROLL_DISTANCE)
                    listOf(
                        beforeScroll,
                        list.layoutManager!!.isSmoothScrolling,
                        list.scrollState == RecyclerView.SCROLL_STATE_SETTLING,
                        list.isLayoutRequested,
                        list.isSettled,
                    )
                }

            state shouldBe listOf(true, false, true, false, false)
        }
    }

    @Test
    fun `a recycler view with an adapter update that no layout pass has processed is not settled`() {
        ActivityScenario.launch(LatchTestActivity::class.java).use { scenario ->
            val state =
                scenario.read { activity ->
                    val adapter = StubAdapter()
                    val list =
                        RecyclerView(activity).apply {
                            layoutManager = LinearLayoutManager(activity)
                            setHasFixedSize(true)
                            this.adapter = adapter
                        }
                    activity.addContentView(list, ViewGroup.LayoutParams(LAYOUT_SIZE, LAYOUT_SIZE))
                    list.laidOut()
                    val beforeInsert = list.isSettled
                    adapter.items += 1
                    adapter.notifyItemInserted(0)
                    listOf(
                        beforeInsert,
                        list.layoutManager!!.isSmoothScrolling,
                        list.scrollState == RecyclerView.SCROLL_STATE_IDLE,
                        list.isLayoutRequested,
                        list.hasPendingAdapterUpdates(),
                        list.isSettled,
                    )
                }

            state shouldBe listOf(true, false, true, false, true, false)
        }
    }

    private fun <V : View> V.laidOut(): V {
        val exactly = MeasureSpec.makeMeasureSpec(LAYOUT_SIZE, MeasureSpec.EXACTLY)
        measure(exactly, exactly)
        layout(0, 0, LAYOUT_SIZE, LAYOUT_SIZE)
        return this
    }

    private class StubAdapter(
        var items: Int = ITEMS,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): RecyclerView.ViewHolder = object : RecyclerView.ViewHolder(View(parent.context)) {}

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int,
        ) = Unit

        override fun getItemCount() = items
    }

    private companion object {
        const val LAYOUT_SIZE = 100
        const val ITEMS = 10
        const val SCROLL_TARGET = 5
        const val SCROLL_DISTANCE = 50
    }
}
