plugins {
    java
    application
    alias(libs.plugins.openrewrite)
    `maven-publish`
}

group = "com.yourorg"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {

    //Openrewrite recipe
    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))
    implementation(libs.openrewrite.java)
    runtimeOnly(libs.openrewrite.java21)

    // JSpecify for nullability annotations
    compileOnly(libs.jspecify)

    // Test dependencies
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.openrewrite.test)

    // OpenRewrite recipe dependencies
    rewrite(platform("org.openrewrite.recipe:rewrite-recipe-bom:3.6.0"))
    rewrite("org.openrewrite:rewrite-gradle")
    rewrite(project)
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(8)
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named("rewriteDryRun") {
    dependsOn("compileJava")
}

tasks.named("rewriteRun") {
    dependsOn("compileJava")
}

rewrite {
    activeRecipe("com.yourorg.SystemOutToLombokLog4jRecipe")
}

application {
    mainClass.set("org.example.Main")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
