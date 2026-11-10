pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content { includeGroup("ai.pleos.playground") }
        }
    }
}
rootProject.name = "FuelPilot"
include(
    ":app",
    ":core:model",
    ":data:energy",
    ":data:location",
    ":data:poi",
    ":data:navigation",
    ":feature:assist",
)
