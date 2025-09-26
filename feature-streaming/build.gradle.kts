plugins {
    // Verwendung von Aliasen aus libs.versions.toml für Konsistenz
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt) // KORREKT: Der Alias für das Hilt-Plugin
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vivid.feature.streaming"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // ENTFERNT: Dieser Block ist bei Verwendung der Compose BOM nicht mehr nötig
    // und kann zu Versionskonflikten führen.
    // composeOptions { ... }
}

// ENTFERNT: Unnötige ksp-Argumente entfernt für eine sauberere Datei.
// ksp { ... }

dependencies {
    // 1. Logische Gruppierung: Projekt-Module
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":features-obs-control"))

    // 2. Android & Lifecycle KTX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.accompanist.permissions)

    // 3. Dependency Injection (Hilt) - Keine Duplikate
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler) // Beachten Sie, dass Ihr Alias "hilt.compiler" lautet
    implementation(libs.androidx.hilt.navigation.compose)

    // 4. Jetpack Compose - Verwendung der Bill of Materials (BOM)
    implementation(platform(libs.androidx.compose.bom)) // WICHTIG: Verwaltet alle Compose-Versionen
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)

    // 5. CameraX Streaming-Input - Keine Duplikate
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    // 6. Media3 (ExoPlayer) für die Wiedergabe
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui.compose)

    // 7. Streaming-Output & Netzwerk - Keine Duplikate
    implementation(libs.rootencoder) // Pedro's RTMP Lib
    implementation(libs.okhttp3.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.java.websocket) // Für OBS Control
    implementation(libs.kotlinx.coroutines.android)

    // 8. Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom)) // BOM auch für Tests
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling) // UI-Tooling für Debug-Builds
    debugImplementation(libs.androidx.ui.test.manifest)
}