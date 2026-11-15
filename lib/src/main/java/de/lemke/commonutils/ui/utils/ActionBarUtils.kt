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

import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity

/** Returns the support action bar, or throws if the activity has none, for example before `setSupportActionBar`. */
internal fun AppCompatActivity.requireSupportActionBar(): ActionBar =
    checkNotNull(supportActionBar) { "The activity has no support action bar: call setSupportActionBar first" }
