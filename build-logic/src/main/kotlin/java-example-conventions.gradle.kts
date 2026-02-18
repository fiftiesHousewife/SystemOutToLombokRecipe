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

    compileOnly(libs.findLibrary("lombok").get())
    annotationProcessor(libs.findLibrary("lombok").get())

    implementation(libs.findLibrary("log4j-api").get())
    runtimeOnly(libs.findLibrary("log4j-core").get())
}
