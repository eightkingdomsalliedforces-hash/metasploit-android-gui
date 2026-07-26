pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "MAGO"
include(
    ":app",
    ":core:common",
    ":core:model",
    ":core:ui",
    ":core:security",
    ":core:rpc",
    ":core:termux",
    ":core:database",
    ":core:datastore",
    ":domain:installation",
    ":domain:metasploit",
    ":feature:onboarding",
    ":feature:dashboard",
    ":feature:diagnostics",
    ":feature:modules",
    ":feature:terminal",
    ":feature:operations",
)
