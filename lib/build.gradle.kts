plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
    id("kotlin-parcelize")
}

android {
    namespace = "de.lemke.commonutils"
    compileSdk = 37
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
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
                it.maxHeapSize = "4096m"
                it.jvmArgs(
                    "-Djunit.platform.launcher.interceptors.enabled=true",
                    "-XX:+EnableDynamicAgentLoading",
                )
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
            }
        }
    }
    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
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
        ktlint(libs.versions.ktlint.get())
    }
    format("xml") {
        target("src/**/*.xml")
        targetExclude("**/build/**")
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
    jvmTarget = "21"
    reports {
        html.required.set(true)
        sarif.required.set(true)
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
                    // Play Core — requires live device + Play Store, untestable in CI
                    "*AppUpdateManagerUtilsKt*",
                    "*InAppReviewUtilsKt*",
                    // Splash screen — zero-logic platform lifecycle hook
                    "*SplashUtilsKt*",
                    // TipPopupUtils — requires OneUI TipPopup widget + Activity decorView root;
                    // the widget cannot be instantiated under Robolectric without a full OneUI theme.
                    "*TipPopupUtilsKt*",
                    // PreferenceUtils — addRelativeLinksCard uses OneUI's listView extension;
                    // the generated lambda/anonymous class cannot be exercised under Robolectric.
                    "*PreferenceUtilsKt\$addShareAppAndRateRelativeLinksCard*",
                    // DrawerOneUIExtensions — setupHeaderAndNavRail and onNavigationSingleClick require OneUI
                    // NavDrawerLayout / DrawerNavigationView and their lambda bodies cannot be exercised in JVM tests.
                    "*DrawerOneUIExtensionsKt*",
                    // DeleteAppDataUtils — deleteAppDataAndExit uses setOnClickListenerWithProgress (OneUI widget)
                    // with a coroutine body that cannot be exercised without a real device context.
                    "*DeleteAppDataUtilsKt*",
                    // restoreSearchAndActionMode is inline; definition-site stubs are phantom.
                    "*DrawerUtilsKt\$restoreSearchAndActionMode\$*",
                    // CommonUtilsLibsActivity setContent {} — Compose lambda body requires full UI rendering,
                    // which cannot run in JVM unit tests without Compose test infrastructure.
                    "*CommonUtilsLibsActivity*",
                    // Default suspend lambda property values — these are the initial instances stored
                    // in companion/top-level properties (e.g. `var getAppVersion = suspend { "" }`).
                    // Every test replaces them before launching the activity, so the defaults are
                    // never invoked; they cannot be reset to their original instance after replacement.
                    "*CommonUtilsAboutActivity\$Companion\$getAppVersion*",
                    "*CommonUtilsSettingsActivity\$Companion\$initPreferences*",
                    "*ActivityUtilsKt\$setupCommonUtilsSettingsActivity*",
                    // setVersionTextView coroutine — the suspend state-machine's suspension-check
                    // instructions are never exercised in JVM tests (coroutine completes synchronously).
                    "*CommonUtilsAboutActivity\$setVersionTextView*",
                    // registerForActivityResult(StartIntentSenderForResult(), ::onActivityResult) and
                    // setMainButtonClickListener(::handleMainButtonClick) each create an anonymous class
                    // inside onCreate; both delegate to @NoCoverage methods → excluded as a group.
                    "*CommonUtilsAboutActivity\$onCreate*",
                    // SettingsRepositoryKt: @get:NoCoverage on `commonUtilsSettings` excludes instruction
                    // miss but Kover 0.9.x does not exclude branch miss for property-getter annotations.
                    "*SettingsRepositoryKt*",
                    // AutoClearedUtilsKt$autoCleared$1: the DESTROYED lifecycle branch in getValue
                    // requires a re-entrant call during Fragment.onDestroyView — not safely reproducible
                    // in unit tests without risking lifecycle-owner access-after-destroy crashes.
                    "*AutoClearedUtilsKt\$autoCleared\$1*",
                    // AboutAppBarListener.onOffsetChanged — else/else-if branches unreachable under Robolectric
                    // because AppBarLayout.totalScrollRange = 0 (no layout engine), making abs >= 0/2 always true.
                    "*CommonUtilsAboutMeActivity\$AboutAppBarListener*",
                    // OOBEActivity initFooterButton coroutine state machine — suspension-check instructions
                    // in invokeSuspend are never exercised because the coroutine completes synchronously.
                    "*CommonUtilsOOBEActivity\$initFooterButton*",
                )
                // inline fun stubs that Kover cannot instrument at definition site
                annotatedBy("de.lemke.commonutils.NoCoverage")
            }
        }
        variant("debug") {
            verify {
                rule { minBound(100, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.INSTRUCTION) }
                rule { minBound(100, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH) }
            }
        }
    }
}
