plugins {
    id("java-recipe-conventions")
    alias(libs.plugins.openrewrite)
    `maven-publish`
}

dependencies {
    runtimeOnly(libs.openrewrite.java17)
    testCompileOnly(libs.lombok)
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-parameters")
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-parameters")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
