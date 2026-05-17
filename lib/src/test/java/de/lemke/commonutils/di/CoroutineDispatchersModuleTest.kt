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

import de.lemke.commonutils.MainDispatcherExtension
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class CoroutineDispatchersModuleTest {
    @Test
    fun `provideIo returns Dispatchers IO`() {
        assertSame(Dispatchers.IO, CoroutineDispatchersModule.provideIo())
    }

    @Test
    fun `provideDefault returns Dispatchers Default`() {
        assertSame(Dispatchers.Default, CoroutineDispatchersModule.provideDefault())
    }

    @Test
    fun `provideMain returns Dispatchers Main`() {
        assertSame(Dispatchers.Main, CoroutineDispatchersModule.provideMain())
    }
}
