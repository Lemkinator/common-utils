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
import java.io.StringReader
import java.util.Properties

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val githubProperties =
    providers.fileContents(layout.rootDirectory.file("github.properties")).asText.map { text ->
        Properties().apply { load(StringReader(text)) }
    }

// Same lookup order as githubProperty in build-logic: github.properties, Gradle property, environment variable.
fun githubProperty(key: String): String =
    githubProperties
        .map { it.getProperty(key) }
        .orElse(providers.gradleProperty(key))
        .orElse(providers.environmentVariable(key.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()))
        .orNull
        ?: throw GradleException("Property $key not found")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.github.com/tribalfs/oneui-design") {
            credentials {
                username = githubProperty("ghUsername")
                password = githubProperty("ghAccessToken")
            }
        }
    }
}

rootProject.name = "Common Utils"
include(":lib")
include(":transitions")
include(":test-support")
