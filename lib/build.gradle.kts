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
                    // CommonUtilsAboutActivity Play Core listener lambdas and thin wrappers —
                    // require live Google Play Store connection, untestable in JVM test environment.
                    // The extracted Play-Core methods are annotated @NoCoverage (caught below via
                    // annotatedBy); these patterns exclude the generated thin-wrapper lambda classes
                    // that delegate to those methods and cannot be excluded via annotatedBy alone.
                    "*CommonUtilsAboutActivity\$checkUpdate\$lambda*",
                    "*CommonUtilsAboutActivity\$onResume\$lambda*",
                    "*CommonUtilsAboutActivity\$onCreate\$lambda\$0\$0",
                    // TipPopupUtils — requires OneUI TipPopup widget + Activity decorView root;
                    // the widget cannot be instantiated under Robolectric without a full OneUI theme.
                    "*TipPopupUtilsKt*",
                    // PreferenceUtils — addRelativeLinksCard (OneUI listView NPE) and
                    // setOnClickListenerWithProgress (OneUI extension) are @NoCoverage;
                    // these globs exclude generated lambda classes inside those methods.
                    "*PreferenceUtilsKt\$addShareAppAndRateRelativeLinksCard*",
                    "*PreferenceUtilsKt\$deleteAppDataAndExit*",
                    // DrawerUtils — setupHeaderAndNavRail and onNavigationSingleClick require OneUI
                    // NavDrawerLayout / DrawerNavigationView; lambda classes from those methods also excluded.
                    "*DrawerUtilsKt\$setupHeaderAndNavRail*",
                    "*DrawerUtilsKt\$onNavigationSingleClick*",
                    // Default empty-lambda objects for crossinline params of inline restoreSearchAndActionMode.
                    "*DrawerUtilsKt\$restoreSearchAndActionMode\$*",
                )
                // inline fun stubs that Kover cannot instrument at definition site
                annotatedBy("de.lemke.commonutils.NoCoverage")
            }
        }
        variant("debug") {
            verify {
                rule { minBound(37, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.INSTRUCTION) }
                rule { minBound(37, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH) }
            }
        }
    }
}
