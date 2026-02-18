plugins {
    java
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))
    implementation(libs.openrewrite.java)
    runtimeOnly(libs.openrewrite.java17)

    // JSpecify for nullability annotations
    compileOnly(libs.jspecify)

    // Lombok needed for recipe implementation and testing
    compileOnly(libs.lombok)
    testCompileOnly(libs.lombok)

    // Log4j2 needed for testing
    testImplementation(libs.bundles.log4j)

    // JUnit 6
    testImplementation(platform("org.junit:junit-bom:${libs.versions.junit.get()}"))
    testImplementation(libs.bundles.junit)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.openrewrite.test)
    testImplementation(libs.assertj.core)
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(8)
    options.compilerArgs.add("-parameters")
}

tasks.named<JavaCompile>("compileTestJava") {
    // Test code can use Java 17 features (including text blocks)
    options.release.set(17)
    options.compilerArgs.add("-parameters")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
