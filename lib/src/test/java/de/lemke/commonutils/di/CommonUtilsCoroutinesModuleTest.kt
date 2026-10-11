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
package de.lemke.commonutils.di

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CommonUtilsCoroutinesModuleTest :
    ShouldSpec({
        should("provide the IO dispatcher for @IoDispatcher") {
            CommonUtilsCoroutinesModule.provideIoDispatcher() shouldBe Dispatchers.IO
        }

        should("provide the Default dispatcher for @DefaultDispatcher") {
            CommonUtilsCoroutinesModule.provideDefaultDispatcher() shouldBe Dispatchers.Default
        }

        should("provide the Main dispatcher for @MainDispatcher") {
            CommonUtilsCoroutinesModule.provideMainDispatcher() shouldBe Dispatchers.Main
        }

        should("run the application scope on the dispatcher it is given") {
            val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined

            val scope = CommonUtilsCoroutinesModule.provideApplicationScope(dispatcher)

            scope.coroutineContext[ContinuationInterceptor] shouldBe dispatcher
        }

        should("keep the application scope active when a child coroutine fails") {
            val scope = CommonUtilsCoroutinesModule.provideApplicationScope(Dispatchers.Unconfined)
            val failing = scope.launch(CoroutineExceptionHandler { _, _ -> }) { error("boom") }

            failing.isCancelled.shouldBeTrue()
            scope.coroutineContext[Job]!!.isActive.shouldBeTrue()
            scope.coroutineContext[Job]!!.isCancelled.shouldBeFalse()
        }
    })
