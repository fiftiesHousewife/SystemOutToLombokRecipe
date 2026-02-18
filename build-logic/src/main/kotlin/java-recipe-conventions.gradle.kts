plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    named<JavaCompile>("compileJava") {
        options.release.set(8)
    }

    named<JavaCompile>("compileTestJava") {
        options.release.set(17)
    }

    withType<Test> {
        useJUnitPlatform()
    }
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    // Note: We use the type-unsafe API to access the version catalog in convention plugins.
    // This is the official Gradle approach as type-safe accessors are not available in
    // precompiled script plugins. See: https://github.com/gradle/gradle/issues/15383

    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))
    implementation(libs.findLibrary("openrewrite-java").get())

    compileOnly(libs.findLibrary("jspecify").get())
    compileOnly(libs.findLibrary("lombok").get())

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation(libs.findLibrary("junit-jupiter-api").get())
    testImplementation(libs.findLibrary("junit-jupiter-params").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testImplementation(libs.findLibrary("openrewrite-test").get())
    testImplementation(libs.findLibrary("log4j-api").get())
    testImplementation(libs.findLibrary("log4j-core").get())

    testRuntimeOnly(libs.findLibrary("junit-jupiter-engine").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}
