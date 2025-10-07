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
        maven {
            url = uri("https://jitpack.io")
//            credentials.username = providers.gradleProperty("authToken").get()
            credentials {
                username = System.getenv("JITPACK_USER") ?: providers.gradleProperty("authToken")
                    .getOrNull()
                password = System.getenv("JITPACK_TOKEN") ?: providers.gradleProperty("authToken")
                    .getOrNull()
            }
        }
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
