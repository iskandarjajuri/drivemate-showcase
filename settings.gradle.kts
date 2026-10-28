rootProject.name = "drivemate-showcase"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        // 👇 Jangan batasi dengan mavenContent filter
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // 👇 Disiplinkan repos di root saja
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 👇 Jangan batasi; Google repo memang perlu luas utk Firebase & friends
        google()
        mavenCentral()
    }
}

include(":composeApp")
include(":shared")