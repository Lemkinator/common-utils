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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PausableDispatcherTest : ShouldSpec(
    {
        should("run a launched block in place while resumed") {
            val delegate = HeldDispatcher()
            val ran = mutableListOf<String>()

            CoroutineScope(PausableDispatcher(delegate) + Job()).launch { ran += "block" }

            ran shouldBe listOf("block")
            delegate.held.shouldBeEmpty()
        }

        should("hold blocks while paused and hand them to the delegate in order on resume") {
            val delegate = HeldDispatcher()
            val dispatcher = PausableDispatcher(delegate)
            val ran = mutableListOf<String>()
            val scope = CoroutineScope(dispatcher + Job())

            dispatcher.pause()
            scope.launch { ran += "first" }
            scope.launch { ran += "second" }

            ran.shouldBeEmpty()
            delegate.held.shouldBeEmpty()

            dispatcher.resume()

            ran.shouldBeEmpty()
            delegate.held shouldHaveSize 2
            delegate.held.forEach(Runnable::run)
            ran shouldBe listOf("first", "second")
        }

        should("run in place again after resume") {
            val delegate = HeldDispatcher()
            val dispatcher = PausableDispatcher(delegate)
            val ran = mutableListOf<String>()

            dispatcher.pause()
            dispatcher.resume()
            CoroutineScope(dispatcher + Job()).launch { ran += "block" }

            ran shouldBe listOf("block")
        }

        should("need a dispatch only while paused") {
            val dispatcher = PausableDispatcher(HeldDispatcher())

            dispatcher.isDispatchNeeded(EmptyCoroutineContext) shouldBe false
            dispatcher.pause()
            dispatcher.isDispatchNeeded(EmptyCoroutineContext) shouldBe true
            dispatcher.resume()
            dispatcher.isDispatchNeeded(EmptyCoroutineContext) shouldBe false
        }

        should("forward a block dispatched directly while resumed to the delegate") {
            val delegate = HeldDispatcher()
            val ran = mutableListOf<String>()

            PausableDispatcher(delegate).dispatch(EmptyCoroutineContext) { ran += "block" }

            ran.shouldBeEmpty()
            delegate.held shouldHaveSize 1
            delegate.held.single().run()
            ran shouldBe listOf("block")
        }
    },
)
