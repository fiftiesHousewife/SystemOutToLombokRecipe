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

dependencies {
    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))
    implementation("org.openrewrite:rewrite-java")

    compileOnly("org.jspecify:jspecify:1.0.0")
    compileOnly("org.projectlombok:lombok:1.18.42")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.apache.logging.log4j:log4j-api:2.25.3")
    testImplementation("org.apache.logging.log4j:log4j-core:2.25.3")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
