// Project identity. The reusable build (toolchain, gates, integrationTest,
// smokeTest, publishing scaffolding) lives in the recipe-library convention
// plugin under build-logic/. Edit gates there or via the recipeLibrary.* keys
// in gradle.properties, not here.
plugins {
    id("recipe-library")
    alias(libs.plugins.cleancode)
}

group = "io.github.fiftieshousewife"
version = "1.1"

// The cleancode plugin sets ignoreFailures=true on Checkstyle, so findings
// land in CI as advisory warnings. We want them to gate the build like
// SpotBugs does — flip back here so any error-level finding fails ./gradlew check.
tasks.withType<Checkstyle>().configureEach {
    isIgnoreFailures = false
    maxErrors = 0
    maxWarnings = 0
}

tasks.named("check") {
    dependsOn("analyseCleanCode")
}

// The cleancode plugin auto-applies SpotBugs / Checkstyle / PMD / CPD to every
// source set. The smokeTest source set is orchestration scaffolding for
// driving nested Gradle processes — quality-gating it gives no signal but
// blocks ./gradlew check. Disable those tasks for the smokeTest source set.
listOf("spotbugsSmokeTest", "checkstyleSmokeTest", "pmdSmokeTest").forEach { name ->
    tasks.findByName(name)?.enabled = false
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "clean-logging", version.toString())

    pom {
        name.set("Clean Logging")
        description.set(
            "OpenRewrite recipes that converge legacy Java logging — System.out, " +
                    "printStackTrace, JUL, Apache Commons Logging, hand-rolled Log4j2 / SLF4J " +
                    "Logger fields — onto Lombok @Slf4j + parameterised SLF4J calls."
        )
        url.set("https://github.com/fiftiesHousewife/clean-logging")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("fiftiesHousewife")
                name.set("Pippa Newbold")
                email.set("pippa.newbold@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/fiftiesHousewife/clean-logging.git")
            developerConnection.set("scm:git:ssh://github.com/fiftiesHousewife/clean-logging.git")
            url.set("https://github.com/fiftiesHousewife/clean-logging")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/fiftiesHousewife/clean-logging")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
