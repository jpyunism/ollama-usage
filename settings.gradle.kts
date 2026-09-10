pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ollama-cloud-usage"
include(":app")
include(":core:model")
include(":core:net")
include(":core:data")
include(":core:notify")
include(":core:ui")
include(":feature:usage")
include(":feature:settings")
include(":feature:stats")
