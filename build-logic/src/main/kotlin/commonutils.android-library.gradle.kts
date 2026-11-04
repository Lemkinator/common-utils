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
import com.android.build.api.dsl.LibraryExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.library")
    id("dev.detekt")
    id("com.diffplug.spotless")
    id("com.autonomousapps.dependency-analysis")
}

group = "io.github.lemkinator"
version = catalog.version("common-utils")

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

extensions.configure<LibraryExtension> {
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(catalog.version("jvmTarget"))
        targetCompatibility = JavaVersion.toVersion(catalog.version("jvmTarget"))
    }

    compileSdk {
        version =
            release(catalog.version("compileSdk").toInt()) {
                minorApiLevel = catalog.version("compileSdkMinor").toInt()
            }
    }
    defaultConfig.minSdk = 26

    lint {
        informational += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        warningsAsErrors = true
        // checkDependencies = false: private AAR deps surface
        // hundreds of unactionable warnings; flip to true once in-project surface is clean
        checkDependencies = false
        // Explicit: pins intent against future AGP default changes.
        checkReleaseBuilds = true
        abortOnError = true
    }

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
}

extensions.configure<KotlinAndroidProjectExtension> {
    explicitApi()
    compilerOptions.optIn.add(INTERNAL_API_MARKER)
}

// oneui-design replaces these AOSP AndroidX modules with Samsung's SESL forks, which
// keep the original package names — exclude the AOSP originals everywhere to prevent
// shadowing.
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

extensions.configure<SpotlessExtension> {
    kotlin {
        target("src/**/*.kt")
        licenseHeaderFile(isolated.rootProject.projectDirectory.file("config/spotless/apache-2.0.kt"))
        ktlint(catalog.version("ktlint"))
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        target("src/**/*.xml")
        licenseHeaderFile(isolated.rootProject.projectDirectory.file("config/spotless/apache-2.0.xml"), "(<[^!?])")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

extensions.configure<DetektExtension> {
    toolVersion = catalog.version("detekt")
    config.setFrom(isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml"))
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
    jvmTarget = catalog.version("jvmTarget")
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}
