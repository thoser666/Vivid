// feature-streaming/build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("dagger.hilt.android.plugin")
//    kotlin("kapt")
    id("com.google.devtools.ksp")
//    id("org.jetbrains.kotlin.plugin.compose") // Add this line
}

android {
    namespace = "com.vivid.feature.streaming" // Updated namespace
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true // Enable Compose for UI elements within the feature module
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // Ensure version matches app module
    }
}

dependencies {
    // Project dependencies
    implementation(project(":domain")) // Feature depends on domain for business logic/models
    implementation(project(":core")) // Feature might use common core utilities

    // Android KTX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Hilt
    //   implementation(libs.hilt.android)
//    ksp(libs.dagger.hilt.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // CameraX für Video-Streaming
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video) // Für Recording

    // Hilt Navigation Compose
    implementation(libs.androidx.hilt.navigation.compose)

    // Permissions (Accompanist)
    implementation(libs.accompanist.permissions)

    // Network für RTMP/SRT Streaming
    implementation(libs.okhttp3.okhttp)

    // Optional: Logging für Debug
    implementation(libs.logging.interceptor)

    // Für WebSocket (OBS Control)
    implementation(libs.java.websocket)

    // Compose (for UI within the feature module)
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.ui.tooling.preview) // For @Preview

    // CameraX (core streaming functionality)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

// Core Media3 (ExoPlayer Nachfolger)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Protocol Support für IRL-Streaming
    implementation(libs.androidx.media3.exoplayer.hls) // HLS Streams
    implementation(libs.androidx.media3.exoplayer.rtsp) // RTSP Streams
    implementation(libs.androidx.media3.exoplayer.dash) // DASH Streams

    // Für lokale Dateien & verschiedene Formate
    implementation(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.datasource)

    // Compose Integration
    implementation(libs.androidx.media3.ui.compose) // Neu!

    // Pedro's RTMP für Live-Streaming Output
    implementation(libs.rootencoder)

    // Network & WebSocket
    implementation(libs.okhttp3.okhttp)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Stream-client
    // implementation(libs.rtmp.rtsp.stream.client.java) // Add this line

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
