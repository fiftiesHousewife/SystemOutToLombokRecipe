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

dependencies {
    val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

    compileOnly(libs.findLibrary("lombok").get())
    annotationProcessor(libs.findLibrary("lombok").get())

    implementation(libs.findLibrary("log4j-api").get())
    runtimeOnly(libs.findLibrary("log4j-core").get())
}
