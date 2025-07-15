import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "de.lemke.commonutils"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    //SESL Android Jetpack
    implementation("sesl.androidx.core:core:1.16.0+1.0.16-sesl7+rev1")
    implementation("sesl.androidx.core:core-ktx:1.16.0+1.0.0-sesl7+rev0")
    implementation("sesl.androidx.appcompat:appcompat:1.7.1+1.0.47000-sesl7+rev0")
    implementation("sesl.androidx.preference:preference:1.2.1+1.0.12-sesl7+rev0")
    //SESL Material Components + Design Lib + Icons
    implementation("sesl.com.google.android.material:material:1.12.0+1.0.39-sesl7+rev5")
    implementation("io.github.tribalfs:oneui-design:0.7.3+oneui7")
    implementation("io.github.oneuiproject:icons:1.1.0")

    implementation("androidx.core:core-splashscreen:1.2.0-rc01")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("com.airbnb.android:lottie:6.6.7")
    implementation("com.google.android.gms:play-services-oss-licenses:17.2.1")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("com.google.android.play:review-ktx:2.0.2")
}

configurations.implementation {
    //Exclude official android jetpack modules
    exclude("androidx.core", "core")
    exclude("androidx.core", "core-ktx")
    exclude("androidx.customview", "customview")
    exclude("androidx.coordinatorlayout", "coordinatorlayout")
    exclude("androidx.drawerlayout", "drawerlayout")
    exclude("androidx.viewpager2", "viewpager2")
    exclude("androidx.viewpager", "viewpager")
    exclude("androidx.appcompat", "appcompat")
    exclude("androidx.fragment", "fragment")
    exclude("androidx.preference", "preference")
    exclude("androidx.recyclerview", "recyclerview")
    exclude("androidx.slidingpanelayout", "slidingpanelayout")
    exclude("androidx.swiperefreshlayout", "swiperefreshlayout")

    //Exclude official material components lib
    exclude("com.google.android.material", "material")
}