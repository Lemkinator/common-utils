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

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable

/** What a [PausableDispatcher] does with a block dispatched while it is resumed. */
public enum class WhileResumed {
    /** Hands the block to the delegate, so no block runs inside the call that dispatches it. */
    Dispatch,

    /**
     * Runs the block in place like [kotlinx.coroutines.Dispatchers.Unconfined], unless the delegate still holds an
     * earlier block: the block then waits behind it.
     */
    RunInPlace,
}

/**
 * A dispatcher that can hold work back: blocks dispatched between [pause] and [resume] wait, and [resume] hands them
 * to [delegate] in dispatch order. A block dispatched after [resume] never overtakes them. While resumed, [whileResumed]
 * decides how a block runs.
 *
 * Use a fresh instance per test: in [WhileResumed.RunInPlace] mode, a block the delegate never runs keeps the
 * dispatcher dispatching for good.
 */
public class PausableDispatcher(
    private val delegate: CoroutineDispatcher,
    private val whileResumed: WhileResumed = WhileResumed.Dispatch,
) : CoroutineDispatcher() {
    private val lock = Any()
    private val held = ArrayDeque<Pair<CoroutineContext, Runnable>>()

    /** Blocks held or handed to the delegate that have not finished yet; counted from the moment of dispatch. */
    private val outstanding = AtomicInteger()

    @Volatile
    private var paused = false

    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        when (whileResumed) {
            WhileResumed.Dispatch -> true
            WhileResumed.RunInPlace -> paused || outstanding.get() > 0
        }

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        synchronized(lock) {
            val task = if (whileResumed == WhileResumed.RunInPlace) counted(block) else block
            if (paused) held.addLast(context to task) else delegate.dispatch(context, task)
        }
    }

    /** Holds every block dispatched from now on until [resume]. */
    public fun pause() {
        paused = true
    }

    /** Hands the held blocks to the delegate in order; later blocks follow [whileResumed] again. */
    public fun resume() {
        synchronized(lock) {
            paused = false
            while (held.isNotEmpty()) {
                val (context, task) = held.removeFirst()
                delegate.dispatch(context, task)
            }
        }
    }

    private fun counted(block: Runnable): Runnable {
        outstanding.incrementAndGet()
        return Runnable {
            try {
                block.run()
            } finally {
                outstanding.decrementAndGet()
            }
        }
    }
}
