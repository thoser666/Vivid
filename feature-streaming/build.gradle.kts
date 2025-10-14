plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vivid.feature.streaming"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Abhängigkeit zum Core-Modul, das die Repositories etc. bereitstellt
    implementation(project(":core"))

    // UI und Navigation für dieses Feature
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Hilt für das ViewModel
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // =============================================================
    // HIER SIND DIE KORREKTEN, AUFGETEILTEN ABHÄNGIGKEITEN
    // WIE IM GITHUB-ISSUE BESCHRIEBEN.
    // 'ConnectCheckerRtmp' ist im ':rtmp'-Modul.
    implementation(libs.rootencoder.encoder)
    implementation(libs.rootencoder.library)
    implementation(libs.rootencoder.rtmp)
    // =============================================================

    // Media3 / ExoPlayer für die Wiedergabe
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Test-Abhängigkeiten
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core.testing)
}
