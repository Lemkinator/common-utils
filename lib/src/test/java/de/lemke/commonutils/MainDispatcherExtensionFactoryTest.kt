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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtensionFactoryTest : ShouldSpec(
    {
        val created = mutableListOf<TestDispatcher>()
        val installed = mutableListOf<TestDispatcher>()
        extension(MainDispatcherExtension { StandardTestDispatcher().also(created::add) })

        repeat(2) { index ->
            should("install a fresh dispatcher the factory returns in root test $index") {
                val scope = CoroutineScope(Dispatchers.Main)
                try {
                    var launched = false
                    scope.launch { launched = true }
                    launched shouldBe false
                    val dispatcher = created.last()
                    dispatcher.scheduler.runCurrent()
                    launched shouldBe true
                    installed += dispatcher
                } finally {
                    scope.cancel()
                }
            }
        }

        afterSpec {
            installed.size shouldBe 2
            installed[0] shouldNotBe installed[1]
            created shouldBe installed

            var ranOnTestDispatcher = false
            shouldThrow<IllegalStateException> {
                Dispatchers.Main.dispatch(EmptyCoroutineContext, Runnable { ranOnTestDispatcher = true })
            }.message shouldContain "Dispatchers.Main was accessed"
            created.forEach { it.scheduler.runCurrent() }
            ranOnTestDispatcher shouldBe false
        }
    },
)
