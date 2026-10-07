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
package de.lemke.commonutils.data

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class FakePackageLookupTest : ShouldSpec(
    {
        lateinit var lookup: FakePackageLookup

        beforeEach { lookup = FakePackageLookup() }

        should("count every installed-apps call") {
            lookup.installedApps()
            lookup.installedApps()

            lookup.installedAppsCalls shouldBe 2
        }

        should("record looked-up package names in call order") {
            lookup.applicationInfo("com.example.first")
            lookup.applicationInfo("com.example.second")

            lookup.lookedUpPackages shouldBe listOf("com.example.first", "com.example.second")
        }

        should("hold installedApps on the gate until it completes") {
            val gate = CompletableDeferred<Unit>()
            lookup.gate = gate
            coroutineScope {
                val call = async(start = CoroutineStart.UNDISPATCHED) { lookup.installedApps() }

                call.isCompleted.shouldBeFalse()
                lookup.installedAppsCalls shouldBe 1

                gate.complete(Unit)
                call.await() shouldBe emptyList()
            }
        }

        should("hold applicationInfo on the gate until it completes") {
            val gate = CompletableDeferred<Unit>()
            lookup.gate = gate
            coroutineScope {
                val call = async(start = CoroutineStart.UNDISPATCHED) { lookup.applicationInfo("com.example.missing") }

                call.isCompleted.shouldBeFalse()
                lookup.lookedUpPackages shouldBe listOf("com.example.missing")

                gate.complete(Unit)
                call.await() shouldBe null
            }
        }
    },
)
