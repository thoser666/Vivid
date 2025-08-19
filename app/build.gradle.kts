plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("io.sentry.android.gradle") version "5.9.0"
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.vivid.irlbroadcaster"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    defaultConfig {
        applicationId = "com.vivid.irlbroadcaster"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = 1
        versionName = "1.0"
        // Only package these locales
        resConfigs("en", "fr", "de")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        // Eventuell eine Beta-Variante
/*        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
        }
*/        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(project(":feature-streaming")) // Add this line
    implementation("androidx.navigation:navigation-compose:2.9.3") // Or the latest version

//    implementation("com.google.dagger:hilt-android:2.44")
//    ksp("com.google.dagger:hilt-compiler:2.44")

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // CameraX für Video-Streaming
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("androidx.camera:camera-video:1.4.0") // Für Recording

    // Hilt Navigation Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Permissions (Accompanist)
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Network für RTMP/SRT Streaming
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Für WebSocket (OBS Control)
    implementation("com.squareup.okhttp3:okhttp-ws:4.12.0") // Falls noch verfügbar
    // oder
//    implementation("org.java-websocket:Java-WebSocket:1.5.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

sentry {
    org.set("privat-jb")
    projectName.set("vivid")

    // this will upload your source code to Sentry to show it as part of the stack traces
    // disable if you don't want to expose your sources
    includeSourceContext.set(true)
}
