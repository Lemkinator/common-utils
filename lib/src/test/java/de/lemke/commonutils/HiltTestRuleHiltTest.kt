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

import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.IoDispatcher
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class HiltTestRuleHiltTest {
    // Outside HiltTestRule, so it observes the state after the rule has closed the resources.
    @get:Rule(order = 0)
    val afterTest = AfterTestRule()

    @get:Rule(order = 1)
    val hiltRule = HiltTestRule(this)

    @BindValue
    @JvmField
    val settings: SettingsRepository = SettingsRepository(freshTestPreferences())

    @Inject
    @IoDispatcher
    lateinit var io: CoroutineDispatcher

    private val closed = mutableListOf<String>()

    @Test
    fun `injects the test instance`() {
        hiltRule.inject()

        io shouldBe Dispatchers.IO
    }

    @Test
    fun `closes registered resources after the test in reverse order`() {
        HiltTestRule.closeAfterTest(AutoCloseable { closed += "first" })
        HiltTestRule.closeAfterTest(AutoCloseable { closed += "second" })
        closed.isEmpty() shouldBe true
        afterTest.check = { closed shouldContainExactly listOf("second", "first") }
    }

    class AfterTestRule : TestRule {
        var check: () -> Unit = {}

        override fun apply(
            base: Statement,
            description: Description,
        ) = object : Statement() {
            override fun evaluate() {
                base.evaluate()
                check()
            }
        }
    }
}
