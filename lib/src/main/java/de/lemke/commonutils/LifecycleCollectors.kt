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
@file:Suppress("unused")

package de.lemke.commonutils

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.Lifecycle.State.STARTED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow

inline fun <T> AppCompatActivity.collectState(
    flow: StateFlow<T>,
    minActiveState: State = STARTED,
    crossinline onEach: (T) -> Unit,
) = launchAndRepeatWithLifecycle(minActiveState) {
    flow.collect { onEach(it) }
}

inline fun <T> Fragment.collectState(
    flow: StateFlow<T>,
    minActiveState: State = STARTED,
    crossinline onEach: (T) -> Unit,
) = launchAndRepeatWithViewLifecycle(minActiveState) {
    flow.collect { onEach(it) }
}

inline fun <T> AppCompatActivity.collectEvents(
    channel: ReceiveChannel<T>,
    minActiveState: State = STARTED,
    crossinline onEach: (T) -> Unit,
) = launchAndRepeatWithLifecycle(minActiveState) {
    for (event in channel) onEach(event)
}

inline fun <T> Fragment.collectEvents(
    channel: ReceiveChannel<T>,
    minActiveState: State = STARTED,
    crossinline onEach: (T) -> Unit,
) = launchAndRepeatWithViewLifecycle(minActiveState) {
    for (event in channel) onEach(event)
}
