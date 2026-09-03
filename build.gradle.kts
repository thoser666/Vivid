allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            when (requested.group to requested.name) {
                "io.netty" to "netty-handler",
                "io.netty" to "netty-codec-http2",
                "io.netty" to "netty-codec-http",
                "io.netty" to "netty-common",
                "io.netty" to "netty-buffer",
                "io.netty" to "netty-transport" -> useVersion("4.1.137.Final")
                "org.apache.commons" to "commons-lang3" -> useVersion("3.18.0")
                "org.bouncycastle" to "bcprov-jdk18on",
                "org.bouncycastle" to "bcpkix-jdk18on",
                "org.bouncycastle" to "bcutil-jdk18on" -> useVersion("1.85")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
    // Kover im Root anwenden: Wurzelmodul ist das Merging-Modul, das die
    // Coverage aller Android-Module sammelt (Dependencies unten).
    alias(libs.plugins.kover)
}

// ── Test-Coverage (Kover) ────────────────────────────────────────────────────
// Wurzelmodul als Merging-Modul: sammelt die Coverage aller Android-Module in
// EINEN JaCoCo-kompatiblen Report (test_most-Nachweis für das OpenSSF-CII-
// Best-Practices-Badge, siehe docs/cii-best-practices-badge.md). Tasks:
//   ./gradlew koverXmlReport  -> app/build/reports/kover/report.xml
//   ./gradlew koverHtmlReport -> app/build/reports/kover/html/index.html
dependencies {
    kover(project(":app"))
    kover(project(":core"))
    kover(project(":domain"))
    kover(project(":data"))
    kover(project(":feature-streaming"))
    kover(project(":feature-settings"))
    kover(project(":feature-chat"))
    kover(project(":feature-widgets"))
    kover(project(":feature-obs-control"))
}

kover {
    reports {
        total {
            // Aggregierter Report über alle Module: nur Produktionstypen
            // zählen, Testcode/Hilt-DI/Generic-Composables rausfiltern.
            filters {
                excludes {
                    annotatedBy(
                        "dagger.hilt.android.AndroidEntryPoint",
                        "dagger.hilt.android.lifecycle.HiltViewModel",
                        "javax.inject.Singleton",
                        "androidx.compose.ui.tooling.preview.Preview",
                    )
                    packages(
                        "hilt_aggregated_deps",
                        "*.di",
                        "dagger.hilt.*",
                    )
                }
            }
            xml { onCheck = false }
            html { onCheck = false }
        }
    }
}
