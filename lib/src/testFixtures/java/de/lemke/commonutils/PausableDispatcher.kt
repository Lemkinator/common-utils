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

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable

/**
 * Runs blocks in place like [kotlinx.coroutines.Dispatchers.Unconfined], except that blocks dispatched between [pause]
 * and [resume] wait, and [resume] hands them to [delegate] in dispatch order.
 */
class PausableDispatcher(
    private val delegate: CoroutineDispatcher,
) : CoroutineDispatcher() {
    private val lock = Any()
    private val held = ArrayDeque<Pair<CoroutineContext, Runnable>>()

    @Volatile
    private var paused = false

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = paused

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        val hold = synchronized(lock) { paused.also { if (it) held.addLast(context to block) } }
        if (!hold) delegate.dispatch(context, block)
    }

    /** Holds every block dispatched from now on until [resume]. */
    fun pause() {
        paused = true
    }

    /** Dispatches the held blocks to the delegate in order and runs later blocks in place again. */
    fun resume() {
        val released =
            synchronized(lock) {
                paused = false
                held.toList().also { held.clear() }
            }
        released.forEach { (context, block) -> delegate.dispatch(context, block) }
    }
}
