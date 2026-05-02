plugins {
    java
    alias(libs.plugins.openrewrite)
    alias(libs.plugins.versions)
    alias(libs.plugins.maven.publish)
    // jacoco + spotbugs are applied by the cleancode plugin; declare neither here.
    alias(libs.plugins.cleancode)
}

group = "io.github.fiftieshousewife"
version = "0.6"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.openrewrite.recipe.bom))
    implementation(libs.openrewrite.java)
    implementation(libs.openrewrite.toml)
    implementation(libs.openrewrite.gradle)
    runtimeOnly(libs.openrewrite.java8)
    runtimeOnly(libs.openrewrite.java11)
    runtimeOnly(libs.openrewrite.java17)
    runtimeOnly(libs.openrewrite.java21)
    runtimeOnly(libs.openrewrite.java25)

    compileOnly(libs.jspecify)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.openrewrite.test)
    testImplementation(libs.openrewrite.properties)
    testImplementation(libs.openrewrite.gradle.tooling.api)
    testImplementation(gradleApi())
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "METHOD"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

spotbugs {
    ignoreFailures.set(false)
    effort.set(com.github.spotbugs.snom.Effort.DEFAULT)
    reportLevel.set(com.github.spotbugs.snom.Confidence.DEFAULT)
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(17)
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(25)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// Integration tests live in their own source set. They drive an embedded
// Gradle daemon via withToolingApi(), and that daemon's bundled Groovy/ASM
// (Gradle 8.14.3 by default) cannot read Java 25 bytecode (class major
// version 69). So integrationTest compiles at release=21 and runs on a
// JDK 21 launcher. Production code stays on release=17 and the rest of
// the build keeps the JDK 25 toolchain.
sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

configurations {
    named("integrationTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("integrationTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
}

tasks.named<JavaCompile>("compileIntegrationTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(21)
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests that drive an embedded Gradle via withToolingApi()."
    group = "verification"
    useJUnitPlatform()

    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath

    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTest)
}

// Smoke tests: scaffold a fresh /tmp Gradle project per recipe variant, run the
// recipe end-to-end via a nested Gradle process, and confirm the rewritten Java
// still compiles. Backs up SMOKE_TEST.md §2 — the bit RewriteTest can't prove
// because there's no real javac + Lombok annotation processor in the loop.
//
// Pinned to Gradle 8.14.3 in the smoke project so we sidestep the Gradle 9
// catalog-handling regression noted in BACKLOG. Daemon runs on JDK 21 because
// Gradle 8.x can't host JDK 25.
//
// Not wired into ./gradlew check — these are minutes, not seconds. They run as
// the pre-publish gate (see publishAndReleaseToMavenCentral wiring below once
// Phase 3 lands).
sourceSets {
    create("smokeTest")
}

configurations {
    named("smokeTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("smokeTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
}

tasks.named<JavaCompile>("compileSmokeTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(21)
}

// The clean-code plugin auto-applies SpotBugs / Checkstyle / PMD / CPD to every
// source set. The smokeTest source set is just orchestration scaffolding for
// driving nested Gradle processes — quality-gating it gives no signal but
// blocks `./gradlew check`. Disable those tasks for the smokeTest source set.
listOf("spotbugsSmokeTest", "checkstyleSmokeTest", "pmdSmokeTest").forEach { name ->
    tasks.findByName(name)?.enabled = false
}

tasks.register<Test>("smokeTest") {
    description = "Scaffolds a /tmp Gradle project per recipe variant, runs the recipe, " +
            "and confirms the rewritten Java compiles. Slower than integrationTest — " +
            "depends on jar + publishToMavenLocal so the throwaway projects can resolve " +
            "the recipe via coordinates."
    group = "verification"
    useJUnitPlatform()

    dependsOn(tasks.named("publishToMavenLocal"))

    val jdk21Home = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.map { it.metadata.installationPath.asFile.absolutePath }

    val rewritePluginVersion = versionCatalogs.named("libs")
            .findVersion("openrewrite").orElseThrow().requiredVersion
    systemProperty("smokeTest.projectGroup", project.group.toString())
    systemProperty("smokeTest.projectVersion", project.version.toString())
    systemProperty("smokeTest.rewritePluginVersion", rewritePluginVersion)
    systemProperty("smokeTest.projectRoot", project.projectDir.absolutePath)
    systemProperty("smokeTest.buildDir", layout.buildDirectory.get().asFile.absolutePath)

    doFirst {
        systemProperty("smokeTest.jdk21Home", jdk21Home.get())
    }

    testClassesDirs = sourceSets["smokeTest"].output.classesDirs
    classpath = sourceSets["smokeTest"].runtimeClasspath

    shouldRunAfter(tasks.test, tasks.named("integrationTest"))
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "system-out-to-lombok-log4j", version.toString())

    pom {
        name.set("System.out to Lombok Log4j")
        description.set("OpenRewrite recipes to convert System.out/err calls to Lombok @Log4j2 logging")
        url.set("https://github.com/fiftiesHousewife/SystemOutToLombokRecipe")
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
            connection.set("scm:git:git://github.com/fiftiesHousewife/SystemOutToLombokRecipe.git")
            developerConnection.set("scm:git:ssh://github.com/fiftiesHousewife/SystemOutToLombokRecipe.git")
            url.set("https://github.com/fiftiesHousewife/SystemOutToLombokRecipe")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/fiftiesHousewife/SystemOutToLombokRecipe")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
