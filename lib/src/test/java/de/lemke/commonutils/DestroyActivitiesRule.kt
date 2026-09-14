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

import android.app.Activity
import org.junit.rules.ExternalResource
import org.robolectric.android.controller.ActivityController

/**
 * Destroys every [ActivityController] registered via [track] after each test.
 *
 * A bare `Robolectric.buildActivity(...).setup().get()` leaves the built Activity live for the
 * rest of the Robolectric sandbox. `AppCompatDelegate.setDefaultNightMode()` is a real static
 * singleton, not a shadow, and recreates every live `AppCompatActivity` process-wide — including
 * one a previous test built and never destroyed — which can then fail a later test's own
 * `ActivityScenario.launch()`. Add `@get:Rule val destroyActivities = DestroyActivitiesRule()` and
 * chain `.track(destroyActivities)` onto every `Robolectric.buildActivity(...).setup()` call.
 */
class DestroyActivitiesRule : ExternalResource() {
    private val controllers = mutableListOf<ActivityController<*>>()

    internal fun register(controller: ActivityController<*>) {
        controllers += controller
    }

    override fun after() {
        controllers.forEach { it.pause().stop().destroy() }
        controllers.clear()
    }
}

/** Registers this controller with [rule] so [DestroyActivitiesRule.after] destroys it. */
fun <T : Activity> ActivityController<T>.track(rule: DestroyActivitiesRule): ActivityController<T> = also { rule.register(it) }
