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

@file:OptIn(ExperimentalRoborazziApi::class)

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.aboutlibraries)
    id("kotlin-parcelize")
}

android {
    namespace = "de.lemke.commonutils"
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
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "de.lemke.commonutils.HiltTestRunner"
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
    buildFeatures {
        viewBinding = true
    }
    lint {
        warningsAsErrors = true
        // checkDependencies = false: private AAR deps surface
        // hundreds of unactionable warnings; flip to true once in-project surface is clean
        checkDependencies = false
        // Explicit: pins intent against future AGP default changes.
        checkReleaseBuilds = true
        abortOnError = true
        baseline = file("lint-baseline.xml")
        // Why: the two display_help_* illustrations ship at a single fixed density
        // (drawable-xxhdpi) on purpose — static help-screen art, not a scaled icon.
        disable += "IconMissingDensityFolder"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true

            all { test ->
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
                test.systemProperty("roborazzi.test.record", project.findProperty("roborazzi.record") ?: "false")
                test.systemProperty("roborazzi.test.verify", project.findProperty("roborazzi.verify") ?: "true")
            }
        }
        animationsDisabled = true
    }
    packaging {
        resources {
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/licenses/**"
        }
    }
    @Suppress("UnstableApiUsage")
    testFixtures {
        enable = true
    }
}

// AGP derives a testFixtures configuration's published capability from the Gradle project name
// (":lib"), not from this module's "common-utils" artifactId override below — so consumers doing
// testFixtures(libs.common.utils) can't resolve it without this. Capabilities are additive, so add
// the correct one alongside AGP's rather than renaming the project (which would also silently
// rename every :lib:* task to :common-utils:*).
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
        outgoing.capability("io.github.lemkinator:common-utils-test-fixtures:${libs.versions.common.utils.get()}")
    }

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
    compare {
        outputDir.set(layout.buildDirectory.dir("reports/roborazzi"))
    }
}

// AboutLibrariesPlugin.apply() unconditionally wires a generated aboutlibraries.json into every
// variant's resources (no supported config scopes this to specific variants - `filterVariants`
// only controls which variant's dependency graph feeds the merged library list, not which variant
// gets the generated resource). Only the debug variant needs it - Robolectric unit tests and
// androidTest both read R.raw.aboutlibraries via the debug variant's merged resources - so disable
// the release-variant generation task to keep the published release AAR byte-equivalent to before
// this plugin was applied.
tasks.matching { it.name == "prepareLibraryDefinitionsRelease" }.configureEach { enabled = false }

dependencies {
    implementation(libs.oneui.design)
    implementation(libs.oneui.icons)
    implementation(libs.app.update)
    implementation(libs.review)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.datastore.preferences.core)
    api(libs.androidx.navigation.fragment.ktx)
    api(libs.core.splashscreen)
    api(libs.lottie)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.konsist)
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.bundles.robolectric.test)
    testImplementation(testFixtures(project(":lib")))

    testFixturesImplementation(libs.androidx.test.core)
    // Compose compiler plugin applies to every Kotlin compile task in this module, including
    // testFixtures — it fails outright if no Compose runtime is on that classpath, even though
    // none of these test helpers use Compose.
    testFixturesImplementation(libs.androidx.material3)
    // PreferenceXmlParity.kt hosts a real PreferenceFragmentCompat (SESL/OneUI's androidx.preference
    // fork, via oneui.design) through Robolectric.buildActivity to inflate preference XML and read
    // defaults. compileOnly, not implementation: a `testFixturesImplementation` runtime dependency here
    // is published on the testFixtures variant's runtime classpath, which androidTestImplementation
    // consumers pull in too - Robolectric merely being present there (never actually the active runner)
    // makes androidx.test's ActivityScenario misdetect a Robolectric sandbox and NPE. Every consumer
    // that calls into this file does so from a JVM Robolectric test, which already puts both on its own
    // runtime classpath (its own testImplementation(robolectric) and its app module's main-source
    // implementation(oneui.design), inherited by src/test).
    testFixturesCompileOnly(libs.oneui.design)
    testFixturesCompileOnly(libs.robolectric)

    // JUnit4 island: Robolectric has no native JUnit5 support, and HiltAndroidRule/@HiltAndroidTest
    // are JUnit4-only. junit-vintage-engine lets the JUnit Platform (useJUnitPlatform() above)
    // discover and run them alongside the rest of this module's Kotest/JUnit5 suite.
    testImplementation(libs.junit4)
    testImplementation(libs.hilt.android.testing)
    testRuntimeOnly(libs.junit.vintage.engine)
    kspTest(libs.hilt.compiler)

    androidTestImplementation(testFixtures(project(":lib")))
    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotest.assertions.core)
    kspAndroidTest(libs.hilt.compiler)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.kt"))
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        target("src/**/*.xml")
        targetExclude("**/build/**")
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.xml"), "(<[^!?])")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = false
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget = libs.versions.jvmTarget.get()
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.databinding.*",
                    "*.BuildConfig",
                    "*Hilt_*",
                    "*_HiltModules*",
                    "*_Factory",
                    "*_Provide*",
                    "*_MembersInjector",
                    "dagger.hilt.*",
                    "hilt_aggregated_deps.*",
                    "*.di.*",
                    // Play Core: requires live device + Play Store; SAM lambdas slip past annotatedBy.
                    "*AppUpdateManagerUtilsKt*",
                    "*InAppReviewUtilsKt*",
                    // SplashUtils: exit-animation lambda drives real ObjectAnimators + lifecycleScope, untestable on JVM.
                    "*SplashUtilsKt*",
                    // TipPopupUtils: requires OneUI TipPopup widget + real decorView root, can't instantiate under Robolectric.
                    "*TipPopupUtilsKt*",
                    // PreferenceUtilsKt: OneUI listView extension lambda, can't exercise under Robolectric.
                    $$"*PreferenceUtilsKt$addShareAppAndRateRelativeLinksCard*",
                    // DrawerUtilsKt: requires OneUI NavDrawerLayout, untestable in JVM tests.
                    $$"*DrawerUtilsKt$setupHeaderAndNavRail*",
                    // PreferenceUtilsKt: deleteAppDataAndExit's coroutine calls clearApplicationUserData(), untestable in JVM.
                    $$"*PreferenceUtilsKt$deleteAppDataAndExit*",
                    // CommonUtilsLibsActivity: Compose setContent{} lambda needs UI rendering; no Compose test infra here.
                    "*CommonUtilsLibsActivity*",
                    // CommonUtilsAboutActivity: onCreate's SAM wrappers fire only via live Play Store callbacks.
                    $$"*CommonUtilsAboutActivity$onCreate*",
                    // AutoClearedUtilsKt: DESTROYED-lifecycle branch needs a re-entrant call during onDestroyView, unsafe to reproduce.
                    $$"*AutoClearedUtilsKt$autoCleared$1*",
                    // AboutAppBarListener: else branches unreachable under Robolectric since totalScrollRange is always 0.
                    $$"*CommonUtilsAboutMeActivity$AboutAppBarListener*",
                    // CommonUtilsOOBEActivity: initFooterButton's coroutine completes synchronously, suspension path unreachable.
                    $$"*CommonUtilsOOBEActivity$initFooterButton*",
                    // LottieUtilsKt: null branch is tested, but JaCoCo loads this continuation class too late to attribute it.
                    $$"*LottieUtilsKt$launchDelayedPlay*",
                    // OnboardingContext$Creator: @Parcelize-generated null-checks are synthetic, never reached directly.
                    $$"*OnboardingContext$Creator*",
                )
                // inline fun definition-site stubs are unreachable under JUnit 5 + Robolectric; see CLAUDE.md §@NoCoverage.
                annotatedBy("de.lemke.commonutils.NoCoverage")
            }
        }
        variant("debug") {
            verify {
                rule {
                    minBound(100, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.INSTRUCTION)
                    minBound(100, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
                }
            }
        }
    }
}
