plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
//    alias(libs.plugins.kotlin.compose)

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
        androidResources {
            localeFilters.addAll(listOf("en", "fr", "de"))
        }
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
*/
        release {
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

    // Replace the old kotlinOptions block with this
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }

    // Add this block if it's not already present and you're not solely relying on the BOM
    // to set the compiler version, or if the BOM isn't aligning correctly.
    composeOptions {
        // Replace "1.5.12" with the actual compatible version you found
        // from the Compose to Kotlin Compatibility Map for Kotlin 1.9.24.
        // THIS VERSION MUST BE THE SAME AS IN YOUR :feature-streaming MODULE.
        kotlinCompilerExtensionVersion = "1.5.14" // Example version, please verify!
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.constraintlayout) // Or the latest version

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(project(":feature-streaming")) // Add this line
    implementation(libs.androidx.navigation.compose) // Or the latest version

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

    // Für WebSocket (OBS Control)
    implementation(libs.java.websocket)

    // Guava
    implementation(libs.guava) // Or the latest compatible version


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
