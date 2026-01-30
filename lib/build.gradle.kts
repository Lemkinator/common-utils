plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    api(libs.bundles.oneui)
    api(libs.bundles.android.play)
    api(libs.play.services.oss.licenses)
    api(libs.core.splashscreen)
    api(libs.lottie)
}