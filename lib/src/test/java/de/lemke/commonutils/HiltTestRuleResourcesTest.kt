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

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

class HiltTestRuleResourcesTest {
    private val closed = mutableListOf<String>()

    @After
    fun drainRegisteredResources() {
        runCatching { runWithRule { } }
    }

    private fun runWithRule(test: () -> Unit) {
        val body =
            object : Statement() {
                override fun evaluate() = test()
            }
        HiltTestRule.closeResourcesRule().apply(body, Description.EMPTY).evaluate()
    }

    private fun resource(
        name: String,
        failure: Throwable? = null,
    ) = AutoCloseable {
        closed += name
        failure?.let { throw it }
    }

    private fun thrownBy(test: () -> Unit): Throwable = runCatching(test).exceptionOrNull() ?: error("nothing was thrown")

    @Test
    fun `closes the registered resources in reverse registration order after the test`() {
        runWithRule {
            HiltTestRule.closeAfterTest(resource("first"))
            HiltTestRule.closeAfterTest(resource("second"))
            HiltTestRule.closeAfterTest(resource("third"))
            closed.shouldBeEmpty()
        }

        closed shouldContainExactly listOf("third", "second", "first")
    }

    @Test
    fun `closes a resource only once`() {
        runWithRule { HiltTestRule.closeAfterTest(resource("once")) }
        runWithRule { }

        closed shouldContainExactly listOf("once")
    }

    @Test
    fun `passes a test that registered nothing`() {
        runWithRule { }

        closed.shouldBeEmpty()
    }

    @Test
    fun `closes every resource and throws the first close failure with the later ones suppressed`() {
        val firstClosed = IllegalStateException("first closed failed")
        val lastClosed = IllegalStateException("last closed failed")

        val failure =
            thrownBy {
                runWithRule {
                    HiltTestRule.closeAfterTest(resource("a", lastClosed))
                    HiltTestRule.closeAfterTest(resource("b"))
                    HiltTestRule.closeAfterTest(resource("c", firstClosed))
                }
            }

        failure shouldBeSameInstanceAs firstClosed
        failure.suppressed.toList() shouldContainExactly listOf(lastClosed)
        closed shouldContainExactly listOf("c", "b", "a")
    }

    @Test
    fun `throws the close failure when the test passes`() {
        val closeFailure = IllegalStateException("close failed")

        val failure = thrownBy { runWithRule { HiltTestRule.closeAfterTest(resource("only", closeFailure)) } }

        failure shouldBeSameInstanceAs closeFailure
        failure.suppressed.toList().shouldBeEmpty()
    }

    @Test
    fun `keeps the test failure and suppresses the close failure with its later ones inside`() {
        val testFailure = AssertionError("test failed")
        val firstClose = IllegalStateException("first close failed")
        val secondClose = IllegalStateException("second close failed")

        val failure =
            thrownBy {
                runWithRule {
                    HiltTestRule.closeAfterTest(resource("a", secondClose))
                    HiltTestRule.closeAfterTest(resource("b", firstClose))
                    throw testFailure
                }
            }

        failure shouldBeSameInstanceAs testFailure
        failure.suppressed.toList() shouldContainExactly listOf(firstClose)
        firstClose.suppressed.toList() shouldContainExactly listOf(secondClose)
        closed shouldContainExactly listOf("b", "a")
    }

    @Test
    fun `rethrows the test failure when every close succeeds`() {
        val testFailure = AssertionError("test failed")

        val failure =
            thrownBy {
                runWithRule {
                    HiltTestRule.closeAfterTest(resource("a"))
                    throw testFailure
                }
            }

        failure shouldBeSameInstanceAs testFailure
        failure.suppressed.toList().shouldBeEmpty()
        closed shouldContainExactly listOf("a")
    }

    @Test
    fun `returns the registered resource`() {
        val closeable = resource("closeable")
        val plain = Any()

        HiltTestRule.closeAfterTest(closeable) shouldBeSameInstanceAs closeable
        HiltTestRule.closeAfterTest(plain) { closed += "plain" } shouldBeSameInstanceAs plain
    }

    @Test
    fun `closes a resource that is no AutoCloseable with the given close function`() {
        val seen = mutableListOf<Any>()
        val plain = Any()

        runWithRule { HiltTestRule.closeAfterTest(plain) { seen += it } }

        seen shouldContainExactly listOf(plain)
    }

    @Test
    fun `counts every registration of either overload`() {
        val before = HiltTestRule.registrations

        HiltTestRule.registrations shouldBe before
        HiltTestRule.closeAfterTest(resource("a"))
        HiltTestRule.closeAfterTest(Any()) { }
        HiltTestRule.registrations shouldBe before + 2
        runWithRule { }
        HiltTestRule.registrations shouldBe before + 2
    }

    @Test
    fun `counts and closes registrations from several threads`() {
        val before = HiltTestRule.registrations
        val executor = Executors.newFixedThreadPool(8)
        val count = 400

        repeat(count) { executor.execute { HiltTestRule.closeAfterTest(Any()) { synchronized(closed) { closed += "x" } } } }
        executor.shutdown()
        executor.awaitTermination(30, TimeUnit.SECONDS) shouldBe true
        runWithRule { }

        HiltTestRule.registrations shouldBe before + count
        closed.size shouldBe count
    }
}
