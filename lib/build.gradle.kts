import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21

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

    kotlin { compilerOptions { jvmTarget.set(JVM_21) } }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    api("io.github.tribalfs:oneui-design:0.8.14+oneui8")
    api("io.github.oneuiproject:icons:1.1.0")
    api("androidx.core:core-splashscreen:1.2.0")
    api("com.airbnb.android:lottie:6.7.1")
    api("com.google.android.gms:play-services-oss-licenses:17.3.0")
    api("com.google.android.play:app-update-ktx:2.1.0")
    api("com.google.android.play:review-ktx:2.0.2")
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