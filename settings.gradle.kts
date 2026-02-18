dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "system-out-to-lombok-log4j"

include("recipes")
include("example")
