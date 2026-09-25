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

import androidx.core.content.FileProvider

/**
 * Clears FileProvider's static per-authority path-strategy cache. Call it before and after each unshadowed Robolectric
 * test that uses FileProvider; [ShadowFileProvider] does not need it.
 */
fun resetFileProviderCache() {
    val cache =
        try {
            val sCache = FileProvider::class.java.getDeclaredField("sCache")
            sCache.isAccessible = true
            sCache.get(null) as MutableMap<*, *>
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException("FileProvider internals changed: expected private static sCache map", e)
        }
    // FileProvider guards sCache with synchronized (sCache).
    synchronized(cache) { cache.clear() }
}
