plugins {
    java
    alias(libs.plugins.openrewrite)
    alias(libs.plugins.versions)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.cleancode)
}

group = "io.github.fiftieshousewife"
version = "0.8"

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

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

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

// JaCoCo 0.8.12 (Gradle 9.4 default) bundles ASM that can't read class file
// major version 69, so the agent throws while instrumenting JDK 25 system
// classes. The build still passes — JaCoCo skips the un-instrumentable classes
// — but tens of stack traces flood the test output. 0.8.13+ adds JDK 25 support.
jacoco {
    toolVersion = "0.8.14"
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

// The cleancode plugin sets ignoreFailures=true on Checkstyle, so findings
// land in CI as advisory warnings. We want them to gate the build like
// SpotBugs does — flip back here so any error-level finding fails ./gradlew check.
tasks.withType<Checkstyle>().configureEach {
    isIgnoreFailures = false
    maxErrors = 0
    maxWarnings = 0
}

// signAllPublications() pulls signing into every publishToMavenLocal, including
// the one smokeTest depends on. In CI there's no GPG key, so signing fails and
// smokeTest never runs. Throwaway smoke projects resolve unsigned artifacts
// fine, so make signing a no-op when no key is configured. Local publish to
// Maven Central is unaffected because the key is in ~/.gradle/gradle.properties.
tasks.withType<Sign>().configureEach {
    onlyIf("a signing key is configured") {
        project.hasProperty("signing.keyId")
                || project.hasProperty("signingInMemoryKey")
                || System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null
    }
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
// Gradle daemon via withToolingApi(), pinned to Gradle 8.14.3. That daemon's
// bundled Groovy/ASM cannot read Java 25 bytecode (class major version 69),
// so integrationTest compiles at release=21 and runs on a JDK 21 launcher.
// Production code stays on release=17 and the rest of the build keeps the
// JDK 25 toolchain.
//
// Why we don't just upgrade to Gradle 9 (which supports JDK 25): rewrite-gradle's
// AddDependency no-ops against build.gradle.kts when libs.versions.toml is
// present and the daemon is Gradle 9.x. See BACKLOG "Re-enable Java 25 in
// integration tests" for the bisect, and UPSTREAM_ISSUE_DRAFT.md for the
// upstream report (file at openrewrite/rewrite). When that ships, drop the
// release=21 / JDK 21 launcher pair below and bump the withToolingApi()
// pins in src/integrationTest to a Gradle 9.x version.
//
// Decoupling the launcher from the daemon JDK (run launcher on 25, keep
// daemon on 21 via BuildLauncher.setJavaHome) was considered and rejected
// in 2026-05: would require a custom withToolingApi() shim that becomes
// dead code the moment upstream lands the catalog fix. Not worth it.
// integrationTest's classpath deliberately excludes sourceSets.test.output:
// test classes compile at release=25 (class file v69), which the embedded
// Gradle 8.14.3's bundled Groovy/ASM cannot read. Including them poisons
// the parser when withToolingApi() walks the classpath. Nothing in
// integrationTest references test/ helpers, so dropping the link is safe.
sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations {
    named("integrationTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("integrationTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
}

// rewrite-java-25 is compiled at JDK 25 bytecode (class file v69). When
// withToolingApi() spins up the embedded Gradle 8.14.3 in-process, its
// bundled Groovy 4.0.21 / ASM 9.6 walks the test JVM's classpath during
// _BuildScript_ semantic analysis and bombs on the first v69 class.
// Production users still ship with rewrite-java-25 (it's runtimeOnly on
// main), but the integration tests don't exercise Java 25 source parsing.
configurations.named("integrationTestRuntimeClasspath") {
    exclude(group = "org.openrewrite", module = "rewrite-java-25")
}

tasks.named<JavaCompile>("compileIntegrationTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(21)
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests that drive an embedded Gradle via withToolingApi()."
    group = "verification"
    useJUnitPlatform()

    // Each test forks a JVM that hosts the Tooling API daemon (rewrite-gradle
    // parser, Kotlin embedded compiler, JaCoCo agent). 512 MB default OOMs once
    // multiple tests run back-to-back in one JVM — the symptom is an
    // intermittent ClassCastException from ParseError to K.CompilationUnit
    // because the build.gradle.kts parse aborts mid-flight under memory pressure.
    maxHeapSize = "2g"

    val jdk21Home = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.map { it.metadata.installationPath.asFile.absolutePath }

    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })

    // The fork itself runs on JDK 21 via the launcher above, but the embedded
    // Gradle 8.14.3 daemon spawned by withToolingApi() reads org.gradle.java.home
    // from the calling JVM's system properties to decide which JDK to start its
    // daemon on. Without this, it inherits JDK 25 from the outer build's toolchain
    // and v69 system classes blow up its bundled Groovy 4.0.21 / ASM 9.6.
    doFirst {
        systemProperty("org.gradle.java.home", jdk21Home.get())
    }

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

// Hard pre-publish gate: every path to Maven Central goes through smokeTest.
// CLAUDE.md's release workflow tells the operator to walk SMOKE_TEST.md by
// hand at step 2, but operator discipline is the wrong place to enforce a
// release gate. Wire the dependency so the gate is structural — there's no
// way to invoke a Maven Central publish task that skips the smoke run.
//
// Note: publishToMavenLocal is NOT gated — smokeTest itself depends on it
// (so the throwaway projects can resolve the recipe via coordinates), and
// gating would be circular.
listOf(
        "publishAndReleaseToMavenCentral",
        "publishToMavenCentral",
        "publishAllPublicationsToMavenCentralRepository",
).forEach { name ->
    tasks.matching { it.name == name }.configureEach { dependsOn("smokeTest") }
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
