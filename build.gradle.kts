plugins {
    java
    jacoco
    alias(libs.plugins.openrewrite)
    alias(libs.plugins.versions)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.spotbugs)
}

group = "io.github.fiftieshousewife"
version = "0.5-SNAPSHOT"

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
