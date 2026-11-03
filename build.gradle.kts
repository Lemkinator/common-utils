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
import com.android.build.api.dsl.CommonExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Properties

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.binary.compatibility.validator) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.roborazzi) apply false
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

/**
 * Converts a camelCase or mixedCase string to ENV_VAR_STYLE (uppercase with underscores).
 * Example: githubAccessToken -> GITHUB_ACCESS_TOKEN
 */
fun String.toEnvVarStyle(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

/**
 * Note: To configure GitHub credentials, you have to generate an access token with at least `read:packages` scope at
 * https://github.com/settings/tokens/new and then add it to any of the following:
 *
 * - Add `ghUsername` and `ghAccessToken` to Global Gradle Properties
 * - Set `GH_USERNAME` and `GH_ACCESS_TOKEN` in your environment variables or
 * - Create a `github.properties` file in your project folder with the following content:
 *      ghUsername=&lt;YOUR_GITHUB_USERNAME&gt;
 *      ghAccessToken=&lt;YOUR_GITHUB_ACCESS_TOKEN&gt;
 */
fun getProperty(key: String): String =
    Properties()
        .apply {
            rootProject
                .file("github.properties")
                .takeIf { it.exists() }
                ?.inputStream()
                ?.use { load(it) }
        }.getProperty(key)
        ?: rootProject.findProperty(key)?.toString()
        ?: System.getenv(key.toEnvVarStyle())
        ?: throw GradleException("Property $key not found")

val commonUtilsVersion =
    libs.versions.common.utils
        .get()
val githubUsername = getProperty("ghUsername")
val githubAccessToken = getProperty("ghAccessToken")

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.github.com/tribalfs/oneui-design") {
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
    }
}

subprojects {
    plugins.withId("com.android.base") {
        project.extensions.findByType(CommonExtension::class.java)?.apply {
            compileOptions.apply {
                sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
                targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
            }

            compileSdk {
                version =
                    release(
                        libs.versions.compileSdk
                            .get()
                            .toInt(),
                    ) {
                        minorApiLevel =
                            libs.versions.compileSdkMinor
                                .get()
                                .toInt()
                    }
            }
            defaultConfig.minSdk = 26

            lint.informational += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
            lint.warningsAsErrors = true
            // checkDependencies = false: private AAR deps surface
            // hundreds of unactionable warnings; flip to true once in-project surface is clean
            lint.checkDependencies = false
            // Explicit: pins intent against future AGP default changes.
            lint.checkReleaseBuilds = true
            lint.abortOnError = true

            testOptions.animationsDisabled = true

            packaging.resources.excludes +=
                setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE*", "META-INF/licenses/**")

            @Suppress("UnstableApiUsage")
            testOptions.managedDevices.localDevices {
                register("pixel9Api35") {
                    device = "Pixel 9"
                    apiLevel = 35
                    systemImageSource = "aosp"
                    testedAbi = "x86_64"
                }
            }

            // oneui-design replaces these AOSP AndroidX modules with Samsung's SESL forks, which
            // keep the original package names — exclude the AOSP originals everywhere to prevent
            // shadowing.
            listOf("com.android.application", "com.android.library").forEach { pluginId ->
                plugins.withId(pluginId) {
                    configurations.configureEach {
                        exclude(group = "androidx.core", module = "core")
                        exclude(group = "androidx.core", module = "core-ktx")
                        exclude(group = "androidx.customview", module = "customview")
                        exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
                        exclude(group = "androidx.drawerlayout", module = "drawerlayout")
                        exclude(group = "androidx.viewpager2", module = "viewpager2")
                        exclude(group = "androidx.viewpager", module = "viewpager")
                        exclude(group = "androidx.appcompat", module = "appcompat")
                        exclude(group = "androidx.fragment", module = "fragment")
                        exclude(group = "androidx.fragment", module = "fragment-ktx")
                        exclude(group = "androidx.preference", module = "preference")
                        exclude(group = "androidx.recyclerview", module = "recyclerview")
                        exclude(group = "androidx.slidingpanelayout", module = "slidingpanelayout")
                        exclude(group = "androidx.swiperefreshlayout", module = "swiperefreshlayout")
                        exclude(group = "com.google.android.material", module = "material")
                    }

                    // exclude() alone is unreliable for androidx.core/core-ktx: several real,
                    // non-SESL-forked libraries (activity, compose.ui, emoji2, autofill, window,
                    // graphics, savedstate) each pull a different real core version, and once
                    // enough conflicting real versions are in one graph, Gradle stops honoring the
                    // exclude rule for this pair. Declaring the SESL fork as an alternate provider
                    // of the real capability, then selecting it, closes that gap.
                    dependencies {
                        components {
                            withModule("sesl.androidx.core:core") {
                                allVariants { withCapabilities { addCapability("androidx.core", "core", id.version) } }
                            }
                            withModule("sesl.androidx.core:core-ktx") {
                                allVariants { withCapabilities { addCapability("androidx.core", "core-ktx", id.version) } }
                            }
                        }
                    }
                    configurations.configureEach {
                        resolutionStrategy.capabilitiesResolution {
                            withCapability("androidx.core:core") { select(candidates.first { it.id.toString().startsWith("sesl.") }) }
                            withCapability("androidx.core:core-ktx") { select(candidates.first { it.id.toString().startsWith("sesl.") }) }
                        }
                    }
                }
            }
        }
    }
    group = "io.github.lemkinator"
    version = commonUtilsVersion

    // Neither the Kotlin Gradle plugin's ABI validation nor the kotlinx plugin registers tasks for an Android
    // library on built-in Kotlin, so the kotlinx task types run against the release compilation.
    plugins.withId("com.android.library") {
        val abiValidationRuntime by configurations.creating {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        dependencies {
            abiValidationRuntime(rootProject.libs.abi.asm)
            abiValidationRuntime(rootProject.libs.abi.asm.tree)
            abiValidationRuntime(rootProject.libs.abi.kotlin.metadata.jvm)
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
    }

    pluginManager.withPlugin("com.diffplug.spotless") {
        extensions.configure<SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.kt"))
                ktlint(libs.versions.ktlint.get())
                trimTrailingWhitespace()
                endWithNewline()
            }
            format("xml") {
                target("src/**/*.xml")
                licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.xml"), "(<[^!?])")
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }

    pluginManager.withPlugin("dev.detekt") {
        extensions.configure<DetektExtension> {
            toolVersion = libs.versions.detekt.get()
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
            parallel = true
            autoCorrect = false
            source.setFrom(
                listOf("main", "test", "testFixtures", "androidTest", "testShared").flatMap { sourceSet ->
                    listOf("java", "kotlin").map { "src/$sourceSet/$it" }
                },
            )
        }
        tasks.withType<Detekt>().configureEach {
            jvmTarget = libs.versions.jvmTarget.get()
            reports {
                html.required.set(true)
                sarif.required.set(true)
            }
        }
    }

    afterEvaluate {
        if (!project.plugins.hasPlugin(
                libs.plugins.maven.publish
                    .get()
                    .pluginId,
            )
        ) {
            return@afterEvaluate
        }
        val artifact = project.extra["artifactId"] as String
        val moduleDescription = requireNotNull(project.description) { "Set description in ${project.path}" }
        println("Evaluated $group:$artifact:$version")

        // AGP derives a testFixtures configuration's published capability from the Gradle project name, not from the
        // artifactId below, so consumers doing testFixtures(libs.common.utils) can't resolve it without this.
        // Capabilities are additive, so add the correct one alongside AGP's rather than renaming the project (which
        // would also silently rename every task path).
        configurations
            .matching {
                it.name in
                    setOf(
                        "releaseTestFixturesApiElements",
                        "releaseTestFixturesRuntimeElements",
                        "releaseTestFixturesVariantReleaseApiPublication",
                        "releaseTestFixturesVariantReleaseRuntimePublication",
                    )
            }.configureEach {
                outgoing.capability("$group:$artifact-test-fixtures:$version")
            }

        project.extensions.configure<PublishingExtension>("publishing") {
            publications {
                create<MavenPublication>("mavenJava") {
                    artifactId = artifact
                    afterEvaluate {
                        from(components["release"])
                    }
                    pom {
                        name = artifact
                        description = moduleDescription
                        url = "https://github.com/Lemkinator/common-utils"
                        developers {
                            developer {
                                id = "Lemkinator"
                                name = "Leonard Lemke"
                                email = "leo@leonard-lemke.com"
                                url = "https://www.leonard-lemke.com"
                                timezone = "Europe/Berlin"
                            }
                        }
                        scm {
                            connection = "scm:git:git://github.com/Lemkinator/common-utils.git"
                            developerConnection = "scm:git:ssh://github.com/Lemkinator/common-utils.git"
                            url = "https://github.com/Lemkinator/common-utils"
                        }
                        issueManagement {
                            system = "GitHub Issues"
                            url = "https://github.com/Lemkinator/common-utils/issues"
                        }
                        licenses {
                            license {
                                name = "Apache-2.0"
                                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                                distribution = "repo"
                            }
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/lemkinator/common-utils")
                    credentials {
                        username = githubUsername
                        password = githubAccessToken
                    }
                }
            }
        }
    }
}
