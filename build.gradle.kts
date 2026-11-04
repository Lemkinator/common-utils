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
    id("commonutils.root")
}

spotless {
    kotlinGradle {
        target(
            fileTree(rootDir) {
                include("**/*.gradle.kts")
                exclude("**/build/**", "**/.gradle/**")
            },
        )
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.kt"), "(^(?![\\/ ]\\*).*$)")
        ktlint(libs.versions.ktlint.get())
    }
    kotlin {
        target("build-logic/src/**/*.kt")
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.kt"))
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

dependencyAnalysis {
    issues {
        all {
            onUnusedDependencies { severity("fail") }
            onIncorrectConfiguration {
                severity("fail")
                exclude("com.google.dagger:hilt-android")
            }
            onUnusedAnnotationProcessors { severity("fail") }
            onUsedTransitiveDependencies {
                severity("warn")
                exclude("com.google.guava:guava")
            }
            onRuntimeOnly { severity("warn") }
            onCompileOnly { severity("warn") }
            onRedundantPlugins { severity("warn") }
            onDuplicateClassWarnings { severity("warn") }
        }
        project(":lib") {
            // testFixtures(common-utils) re-exports the transitions fixtures to consumers.
            onUnusedDependencies { exclude(":transitions") }
        }
    }
    structure {
        bundle("hilt") {
            primary(libs.hilt.android)
            includeDependency("com.google.dagger:dagger")
            includeDependency("com.google.dagger:hilt-core")
            includeDependency("javax.inject:javax.inject")
        }
        bundle("oneui") {
            primary(libs.oneui.design)
            include("^sesl[.].*")
        }
        bundle("kotest") {
            primary(libs.kotest.runner.junit5)
            include("^io[.]kotest:kotest-(assertions-shared|common|framework-engine|runner-junit5)(-jvm|-android)?$")
        }
        bundle("mockk") {
            primary(libs.mockk)
            include("^io[.]mockk:mockk(-core|-dsl)?(-jvm)?$")
        }
        bundle("robolectric") {
            primary(libs.robolectric)
            include("^org[.]robolectric:(annotations|shadows-framework)$")
        }
    }
}
