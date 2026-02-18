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

// Set common properties for all projects
gradle.beforeProject {
    group = "com.yourorg"
    version = "1.0-SNAPSHOT"
}
