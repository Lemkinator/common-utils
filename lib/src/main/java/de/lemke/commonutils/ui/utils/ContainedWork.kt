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
package de.lemke.commonutils.ui.utils

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

// A boundary call breaks its contract by throwing; a state holder must not stay busy or crash its scope. A
// CancellationException that leaves the scope alive (e.g. a withTimeout inside the call) is such a failure;
// ensureActive rethrows one that cancels the scope.
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> runContained(
    tag: String,
    what: String,
    failure: T,
    work: suspend () -> T,
): T =
    try {
        work()
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        Log.e(tag, "$what canceled", e)
        failure
    } catch (e: Exception) {
        Log.e(tag, "$what failed", e)
        failure
    }
