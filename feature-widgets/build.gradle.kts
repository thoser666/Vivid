import java.time.Duration

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kover)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vivid.feature.widgets"

    // Robolectric 4.14.1 bündelt ein älteres ASM, das JDK-25-Klassendateien
    // (major version 69) nicht lesen kann — beim Instrumentieren crasht es mit
    // "Unsupported class file major version 69". Neuere ASM wird für die
    // Unit-Test-Runtime erzwungen (gleicher Workaround wie in feature-streaming).
    configurations.configureEach {
        if (name.contains("UnitTestRuntimeClasspath")) {
            resolutionStrategy {
                force("org.ow2.asm:asm:9.10.1")
                force("org.ow2.asm:asm-tree:9.10.1")
                force("org.ow2.asm:asm-commons:9.10.1")
                force("org.ow2.asm:asm-util:9.10.1")
                force("org.ow2.asm:asm-analysis:9.10.1")
            }
        }
    }

    lint {
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
        warningsAsErrors = true
    }
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
        // Robolectric: gemergtes Manifest + Ressourcen in die JVM-Tests laden
        // (nötig für Compose-UI-Tests mit String-Ressourcen).
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<Test>().configureEach {
    // Failsafe: kill hanging tests after 10 min (CI deadlock protection)
    timeout.set(Duration.ofMinutes(10))
}

dependencies {
    // Modules
    implementation(project(":core"))
    implementation(project(":domain"))

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler.ksp)
    implementation(libs.androidx.hilt.navigation.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ConstraintLayout for Compose (für Widget-Positionierung)
    implementation(libs.androidx.constraintlayout)

    // Coil for Image loading
    implementation(libs.coil.compose)

    // QR-Code-Generierung
    implementation(libs.zxing.core)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    // Robolectric-Compose-UI-Tests (JVM): echte Ressourcen + Semantics-Tree.
    testImplementation(libs.robolectric.core)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
}
