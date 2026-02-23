plugins {
    java
    alias(libs.plugins.openrewrite)
    alias(libs.plugins.versions)
    `maven-publish`
}

group = "org.fifties.housewife"
version = "0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.openrewrite.recipe.bom))
    implementation(libs.openrewrite.java)
    runtimeOnly(libs.openrewrite.java21)

    compileOnly(libs.jspecify)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation( libs.assertj.core)
    testImplementation(libs.openrewrite.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(8)
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
