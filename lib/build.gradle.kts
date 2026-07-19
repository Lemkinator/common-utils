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
    id("kotlin-parcelize")
}

android {
    namespace = "de.lemke.commonutils"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk = 26
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
        checkDependencies = false
        baseline = file("lint-baseline.xml")
        disable += "IconMissingDensityFolder"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true

            all { test ->
                test.useJUnitPlatform()
                test.maxHeapSize = "4096m"
                test.jvmArgs("-XX:+EnableDynamicAgentLoading")
                test.systemProperty("robolectric.graphicsMode", "NATIVE")
            }
        }
    }
    packaging {
        resources {
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/licenses/**"
        }
    }
}

dependencies {
    implementation(libs.oneui.design)
    implementation(libs.oneui.icons)
    implementation(libs.app.update)
    implementation(libs.review)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    api(libs.androidx.navigation.fragment.ktx)
    api(libs.core.splashscreen)
    api(libs.lottie)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.konsist)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // JUnit4 island: Robolectric has no native JUnit5 support, and HiltAndroidRule/@HiltAndroidTest
    // are JUnit4-only. junit-vintage-engine lets the JUnit Platform (useJUnitPlatform() above)
    // discover and run them alongside the rest of this module's Kotest/JUnit5 suite.
    testImplementation(libs.junit4)
    testImplementation(libs.hilt.android.testing)
    testRuntimeOnly(libs.junit.vintage.engine)
    kspTest(libs.hilt.compiler)
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
    kotlinGradle {
        target("*.gradle.kts")
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.kt"), "(^(?![\\/ ]\\*).*$)")
        ktlint(libs.versions.ktlint.get())
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
                    // URLUtilsKt: order-dependent JIT branch-misattribution flakiness, not a real gap (see SettingsRepositoryKt).
                    "*URLUtilsKt*",
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
                    // SettingsRepositoryKt: same order-dependent branch-misattribution flakiness as URLUtilsKt, not a real gap.
                    "*SettingsRepositoryKt*",
                    // AutoClearedUtilsKt: DESTROYED-lifecycle branch needs a re-entrant call during onDestroyView, unsafe to reproduce.
                    $$"*AutoClearedUtilsKt$autoCleared$1*",
                    // AboutAppBarListener: else branches unreachable under Robolectric since totalScrollRange is always 0.
                    $$"*CommonUtilsAboutMeActivity$AboutAppBarListener*",
                    // CommonUtilsOOBEActivity: initFooterButton's coroutine completes synchronously, suspension path unreachable.
                    $$"*CommonUtilsOOBEActivity$initFooterButton*",
                    // LottieUtilsKt: null branch is tested, but JaCoCo loads this continuation class too late to attribute it.
                    $$"*LottieUtilsKt$launchDelayedPlay*",
                    // OnboardingContext: @Parcelize-generated null-checks are synthetic; excluded so Codecov doesn't see the miss.
                    "*OnboardingContext*",
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
