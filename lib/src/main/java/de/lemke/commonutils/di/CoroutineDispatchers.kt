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
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Hilt qualifier for [kotlinx.coroutines.Dispatchers.IO]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Hilt qualifier for [kotlinx.coroutines.Dispatchers.Default]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Hilt qualifier for [kotlinx.coroutines.Dispatchers.Main]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/** Hilt module that provides coroutine dispatcher bindings for [IoDispatcher], [DefaultDispatcher], and [MainDispatcher]. */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineDispatchersModule {
    /** Provides [kotlinx.coroutines.Dispatchers.IO] for I/O-bound work. */
    @Provides
    @IoDispatcher
    fun provideIo(): CoroutineDispatcher = Dispatchers.IO

    /** Provides [kotlinx.coroutines.Dispatchers.Default] for CPU-bound work. */
    @Provides
    @DefaultDispatcher
    fun provideDefault(): CoroutineDispatcher = Dispatchers.Default

    /** Provides [kotlinx.coroutines.Dispatchers.Main] for UI-thread work. */
    @Provides
    @MainDispatcher
    fun provideMain(): CoroutineDispatcher = Dispatchers.Main
}
