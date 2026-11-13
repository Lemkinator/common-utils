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

val robolectricAndroidAllJars =
    catalog
        .findBundle("robolectric-android-all")
        .get()
        .get()
        // One configuration per jar: the jars share a module, so a single configuration would keep only the newest.
        .map { jar -> configurations.detachedConfiguration(dependencies.create(jar)).apply { isTransitive = false } }

tasks.register<Sync>("syncRobolectricJars") {
    from(robolectricAndroidAllJars)
    into(layout.buildDirectory.dir("robolectric-jars"))
}
