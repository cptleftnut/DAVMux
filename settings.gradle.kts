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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "DAVMux"

// DAVMux app
include(":app")

// Termux terminal engine (remixed into DAVMux)
include(":terminal-emulator")
include(":terminal-view")
include(":termux-shared")
