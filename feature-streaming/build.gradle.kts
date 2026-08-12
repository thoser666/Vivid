plugins {
    alias(libs.plugins.android.library)
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

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    // Abhängigkeit zum Core-Modul, das die Repositories etc. bereitstellt
    implementation(project(":core"))
    // Domain-Modell (AppSettings) für die Stream-Einstellungen
    implementation(project(":domain"))

    // AndroidX Core (ContextCompat u. a. für Runtime-Permissions)
    implementation(libs.androidx.core.ktx)

    // UI und Navigation für dieses Feature
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // CameraX für CameraScreen
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

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
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.core.testing)
}
