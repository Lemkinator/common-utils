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

import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Property delegate that automatically clears it's value
 * on the Fragment's onDestroyView as required in
 * https://developer.android.com/topic/libraries/view-binding#fragments
 */
fun <T> Fragment.autoCleared(initialize: () -> T): ReadOnlyProperty<Fragment, T> =
    object : ReadOnlyProperty<Fragment, T>, DefaultLifecycleObserver {
        private var cachedValue: T? = null

        override fun onDestroy(owner: LifecycleOwner) {
            cachedValue = null
        }

        override fun getValue(
            thisRef: Fragment,
            property: KProperty<*>,
        ): T =
            cachedValue ?: initialize().also {
                cachedValue = it
                viewLifecycleOwner.lifecycle.addObserver(this)
            }
    }
