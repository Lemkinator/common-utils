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
                test.jvmArgs(
                    "-Djunit.platform.launcher.interceptors.enabled=true",
                    "-XX:+EnableDynamicAgentLoading",
                )
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
    testImplementation(libs.kotest.extensions.robolectric)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
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
                    // Play Core: requires live device + Play Store, untestable in CI.
                    // @NoCoverage on functions covers the methods; these patterns cover the
                    // anonymous listener classes generated by the lambdas.
                    $$"*AppUpdateManagerUtilsKt$onAppUpdateAvailable*",
                    $$"*InAppReviewUtilsKt$showInAppReview*",
                    // SplashUtils: the setOnExitAnimationListener lambda body (compiled via invokedynamic
                    // into private static methods of SplashUtilsKt) creates ObjectAnimators and launches
                    // a lifecycleScope coroutine — untestable in JVM unit tests.
                    "*SplashUtilsKt*",
                    // TipPopupUtils: requires OneUI TipPopup widget + Activity decorView root;
                    // the widget cannot be instantiated under Robolectric without a full OneUI theme.
                    "*TipPopupUtilsKt*",
                    // PreferenceUtils: addRelativeLinksCard uses OneUI's listView extension;
                    // the generated lambda/anonymous class cannot be exercised under Robolectric.
                    $$"*PreferenceUtilsKt$addShareAppAndRateRelativeLinksCard*",
                    // setupHeaderAndNavRail: openAboutActivity() helper + its method-reference SAM wrapper;
                    // requires OneUI NavDrawerLayout, untestable in JVM tests.
                    $$"*DrawerUtilsKt$setupHeaderAndNavRail*",
                    // deleteAppDataAndExit coroutine continuation: the suspend lambda body
                    // { deleteAppData() } compiles as an inner class whose invokeSuspend calls
                    // deleteAppData() (delay + clearApplicationUserData), untestable in JVM.
                    $$"*PreferenceUtilsKt$deleteAppDataAndExit*",
                    // CommonUtilsLibsActivity setContent {}: Compose lambda body requires full UI rendering,
                    // which cannot run in JVM unit tests without Compose test infrastructure.
                    "*CommonUtilsLibsActivity*",
                    // Default suspend lambda property values: these are the initial instances stored
                    // in companion/top-level properties (e.g. `var getAppVersion = suspend { "" }`).
                    // Every test replaces them before launching the activity, so the defaults are
                    // never invoked; they cannot be reset to their original instance after replacement.
                    $$"*CommonUtilsAboutActivity$Companion$getAppVersion*",
                    $$"*CommonUtilsSettingsActivity$Companion$initPreferences*",
                    $$"*ActivityUtilsKt$setupCommonUtilsSettingsActivity*",
                    // setVersionTextView coroutine: suspend state-machine's suspension-check
                    // instructions are never exercised in JVM tests (coroutine completes synchronously).
                    $$"*CommonUtilsAboutActivity$setVersionTextView*",
                    // registerForActivityResult and setMainButtonClickListener lambdas inside onCreate
                    // generate SAM-wrapper classes/methods excluded here; these fire only via live
                    // Play Store callbacks and cannot be triggered in JVM unit tests.
                    $$"*CommonUtilsAboutActivity$onCreate*",
                    // SettingsRepositoryKt: @get:NoCoverage on `commonUtilsSettings` excludes instruction
                    // miss, but Kover 0.9.x does not exclude branch miss for property-getter annotations.
                    "*SettingsRepositoryKt*",
                    // AutoClearedUtilsKt$autoCleared$1: the DESTROYED lifecycle branch in getValue
                    // requires a re-entrant call during Fragment.onDestroyView - not safely reproducible
                    // in unit tests without risking lifecycle-owner access-after-destroy crashes.
                    $$"*AutoClearedUtilsKt$autoCleared$1*",
                    // AboutAppBarListener.onOffsetChanged: else/else-if branches unreachable under Robolectric
                    // because AppBarLayout.totalScrollRange = 0 (no layout engine), making abs >= 0/2 always true.
                    $$"*CommonUtilsAboutMeActivity$AboutAppBarListener*",
                    // OOBEActivity initFooterButton coroutine state machine: suspension-check instructions
                    // in invokeSuspend are never exercised because the coroutine completes synchronously.
                    $$"*CommonUtilsOOBEActivity$initFooterButton*",
                    // OnboardingContext @Parcelize: createFromParcel null-checks each String field; on
                    // Linux/CI the branch is attributed to `Parcelable` but is never
                    // triggered in any test path. Kover's verify already excludes synthetic null-checks;
                    // exclude the class (and its @Parcelize-generated inner classes via *) so the
                    // JaCoCo XML doesn't expose the miss to Codecov.
                    "*OnboardingContext*",
                )
                // inline fun stubs that Kover cannot instrument at definition site
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
