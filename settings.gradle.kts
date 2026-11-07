pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Ditto"
include(":app")
include(":design")
// Tumble / Shore sea-glass kit, vendored under linen-tumble/. Included by path so
// Ditto's toolchain (AGP/Kotlin/Gradle) builds them; see linen-tumble/AGENTS.md.
include(":seaglass-core")
project(":seaglass-core").projectDir = file("linen-tumble/seaglass-core")
include(":seaglass-compose")
project(":seaglass-compose").projectDir = file("linen-tumble/seaglass-compose")
project(":seaglass-compose").buildFileName = "ditto.build.gradle.kts"
