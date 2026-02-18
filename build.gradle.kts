plugins {
    java
    application
    alias(libs.plugins.openrewrite)
    alias(libs.plugins.versions)
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
    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))
    implementation(libs.openrewrite.java)
    runtimeOnly(libs.openrewrite.java21)

    compileOnly(libs.jspecify)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.openrewrite.test)

    rewrite(platform(libs.openrewrite.recipe.bom))
    rewrite(libs.openrewrite.gradle)
    rewrite(project)
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
