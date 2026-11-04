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
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("commonutils.android-library")
}

// Neither the Kotlin Gradle plugin's ABI validation nor the kotlinx plugin registers tasks for an Android
// library on built-in Kotlin, so the kotlinx task types run against the release compilation.
val abiValidationRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    abiValidationRuntime(catalog.library("abi-asm"))
    abiValidationRuntime(catalog.library("abi-asm-tree"))
    abiValidationRuntime(catalog.library("abi-kotlin-metadata-jvm"))
}

fun registerApiBuild(
    taskName: String,
    compilationName: String,
    dumpName: String,
): TaskProvider<KotlinApiBuildTask> {
    val build =
        tasks.register<KotlinApiBuildTask>(taskName) {
            description = "Builds the public ABI dump of the $compilationName compilation."
            runtimeClasspath.from(abiValidationRuntime)
            nonPublicMarkers.add(INTERNAL_API_MARKER)
            outputApiFile.set(layout.buildDirectory.file("api/$dumpName.api"))
        }
    extensions.getByType<KotlinAndroidProjectExtension>().target.compilations.configureEach {
        if (name == compilationName) {
            build.configure {
                inputClassesDirs.from(compileTaskProvider.flatMap { (it as KotlinJvmCompile).destinationDirectory })
            }
        }
    }
    return build
}

fun registerApiCompare(
    taskName: String,
    dumpName: String,
    build: TaskProvider<KotlinApiBuildTask>,
): TaskProvider<KotlinApiCompareTask> =
    tasks.register<KotlinApiCompareTask>(taskName) {
        group = "verification"
        description = "Checks the public ABI against the committed api/$dumpName.api dump."
        projectApiFile.set(layout.projectDirectory.file("api/$dumpName.api"))
        generatedApiFile.set(build.flatMap { it.outputApiFile })
    }

val apiBuild = registerApiBuild("apiBuild", "release", project.name)
val apiBuildTestFixtures = registerApiBuild("apiBuildTestFixtures", "releaseTestFixtures", "${project.name}-testFixtures")
val apiCheck = registerApiCompare("apiCheck", project.name, apiBuild)
val apiCheckTestFixtures = registerApiCompare("apiCheckTestFixtures", "${project.name}-testFixtures", apiBuildTestFixtures)
apiCheck.configure { dependsOn(apiCheckTestFixtures) }

tasks.register<Copy>("apiDump") {
    group = "other"
    description = "Overwrites api/${project.name}.api and api/${project.name}-testFixtures.api with the current public ABI."
    from(apiBuild.flatMap { it.outputApiFile })
    from(apiBuildTestFixtures.flatMap { it.outputApiFile })
    into(layout.projectDirectory.dir("api"))
}

tasks.named("check") { dependsOn(apiCheck) }
