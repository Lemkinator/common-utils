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
plugins {
    id("commonutils.android-library")
}

description = "Robolectric rules and launch-latch probes shared by the tests of the common-utils modules; not published."

android {
    namespace = "de.lemke.commonutils.testsupport"
}

dependencies {
    api(project(":transitions"))
    api(libs.androidx.activity)
    api(libs.oneui.design)
    api(libs.robolectric)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
}
