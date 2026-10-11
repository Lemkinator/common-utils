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

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.freshTestPreferences
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The include an app declares to opt in to [CommonUtilsCoroutinesModule]. */
@Module(includes = [CommonUtilsCoroutinesModule::class])
@InstallIn(SingletonComponent::class)
interface CommonUtilsCoroutinesTestInclude

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class CommonUtilsCoroutinesModuleHiltTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val settings: SettingsRepository = SettingsRepository(freshTestPreferences())

    @Inject
    @IoDispatcher
    lateinit var io: CoroutineDispatcher

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    @Inject
    @MainDispatcher
    lateinit var main: CoroutineDispatcher

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    @ApplicationScope
    lateinit var sameApplicationScope: CoroutineScope

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `an app that includes the module gets each qualified dispatcher`() {
        io shouldBe Dispatchers.IO
        defaultDispatcher shouldBe Dispatchers.Default
        main shouldBe Dispatchers.Main
    }

    @Test
    fun `an app that includes the module gets one application scope on the default dispatcher`() {
        applicationScope shouldBe sameApplicationScope
        applicationScope.coroutineContext[ContinuationInterceptor] shouldBe Dispatchers.Default
    }
}
