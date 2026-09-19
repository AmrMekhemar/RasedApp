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
rootProject.name = "Rased"
include(":app")
include(":core:ui", ":core:excel")
include(":core:database")
include(":feature:sorting", ":feature:unloading", ":feature:checking")
