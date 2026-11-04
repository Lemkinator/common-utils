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
    id("commonutils.publishing")
    id("commonutils.api-validation")
    id("commonutils.coverage")
}

description = "Android-only activity transitions, splash exit and predictive-back helpers for my Android projects."
commonUtilsPublishing {
    artifactId = "common-utils-transitions"
}

android {
    namespace = "de.lemke.commonutils.transitions"
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.useJUnitPlatform()
            test.maxHeapSize = "4096m"
            test.jvmArgs(
                "-XX:+EnableDynamicAgentLoading",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
            )
            test.systemProperty("robolectric.graphicsMode", "NATIVE")
        }
    }
    @Suppress("UnstableApiUsage")
    testFixtures {
        enable = true
    }
}

dependencies {
    api(libs.oneui.design)
    api(libs.core.splashscreen)
    implementation(libs.review)

    testImplementation(testFixtures(project(":transitions")))
    testImplementation(project(":test-support"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.vintage.engine)

    testFixturesApi(libs.androidx.test.core)
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    // Play Core: requires live device + Play Store; SAM lambdas slip past annotatedBy.
                    "*InAppReviewUtilsKt*",
                    // SplashUtils: exit-animation lambda drives real ObjectAnimators + lifecycleScope, untestable on JVM.
                    "*SplashUtilsKt*",
                )
                annotatedBy("de.lemke.commonutils.transitions.NoCoverage")
            }
        }
    }
}
