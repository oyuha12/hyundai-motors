plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.oyuha12.hyundaimotors"
    compileSdk = 37

    // AAOS Car API, which the Pleos SDK policy names as the source of vehicle speed.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "io.github.oyuha12.hyundaimotors"
        // Pleos Connect requires API 28 or higher (signing key rotation).
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.material)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.texttospeech)
    implementation(libs.pleos.fusedlocation)

    testImplementation(libs.junit)
}
