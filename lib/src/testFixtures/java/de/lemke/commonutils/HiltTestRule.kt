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

import dagger.hilt.android.testing.HiltAndroidRule
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The one Hilt rule of a `@HiltAndroidTest`: wraps [HiltAndroidRule] and closes, after the test, every resource
 * registered with [closeAfterTest]. Hilt discards the per-test component without closing what it holds, such as an
 * in-memory Room database.
 *
 * Resources close in reverse registration order, before Hilt tears the component down. A failing close never skips
 * the remaining closes. If the test failed, its failure is thrown and every close failure is suppressed into it.
 * Otherwise the first close failure is thrown and the later ones are suppressed into it.
 */
public class HiltTestRule(
    testInstance: Any,
) : TestRule {
    private val hiltRule = HiltAndroidRule(testInstance)

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement = hiltRule.apply(closeResourcesRule().apply(base, description), description)

    public fun inject(): Unit = hiltRule.inject()

    public companion object {
        private val resources = ConcurrentLinkedDeque<AutoCloseable>()
        private val registered = AtomicInteger()

        /** How many resources were registered with [closeAfterTest] since the process started. */
        public val registrations: Int get() = registered.get()

        /** Closes [resource] after the running test and returns it. */
        public fun <T : AutoCloseable> closeAfterTest(resource: T): T = closeAfterTest(resource, AutoCloseable::close)

        /** Calls [close] on [resource] after the running test and returns [resource]; for types that are no [AutoCloseable]. */
        public fun <T> closeAfterTest(
            resource: T,
            close: (T) -> Unit,
        ): T {
            resources.addFirst { close(resource) }
            registered.incrementAndGet()
            return resource
        }

        /**
         * Closes every registered resource after the test. A test that runs on `HiltTestApplication` without
         * `@HiltAndroidTest` declares it as `@get:Rule`; [HiltTestRule] already includes it.
         */
        public fun closeResourcesRule(): TestRule = TestRule { base, _ -> closeResourcesAfter(base) }

        private fun closeResourcesAfter(base: Statement): Statement =
            object : Statement() {
                override fun evaluate() {
                    val testFailure = runCatching { base.evaluate() }.exceptionOrNull()
                    val closeFailure = closeRegisteredResources()
                    when {
                        testFailure != null -> {
                            closeFailure?.let(testFailure::addSuppressed)
                            throw testFailure
                        }
                        closeFailure != null -> throw closeFailure
                    }
                }
            }

        private fun closeRegisteredResources(): Throwable? =
            generateSequence(resources::pollFirst).fold(null as Throwable?) { failure, resource ->
                val error = runCatching { resource.close() }.exceptionOrNull()
                when {
                    error == null -> failure
                    failure == null -> error
                    else -> failure.also { it.addSuppressed(error) }
                }
            }
    }
}
