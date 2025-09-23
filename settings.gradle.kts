pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal() // <-- Make sure this is present
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Vivid"
include(":app")
include(":feature-streaming")
include(":domain")
include(":core")
include(":data")
include(":feature-chat")
include(":feature-widgets")
include(":feature-settings")
include(":features-obs-control")
