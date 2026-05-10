package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.toml.Assertions.toml;

@DisplayName("AddSlf4jDependencies end-to-end with real GradleProject marker")
class AddSlf4jDependenciesIntegrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi())
                .recipe(Environment.builder()
                        .scanRuntimeClasspath("io.github.fiftieshousewife")
                        .build()
                        .activateRecipes("io.github.fiftieshousewife.cleanlogging.AddSlf4jDependencies"));
    }

    @Test
    @DisplayName("inline (no catalog): both runtimeOnly log4j2 deps survive the same-group dedup")
    void inline_bothRuntimeOnlyDepsPresent() {
        rewriteRun(
                buildGradleKts(
                        """
                                plugins {
                                    java
                                }
                                repositories {
                                    mavenCentral()
                                }
                                """,
                        """
                                plugins {
                                    java
                                }
                                repositories {
                                    mavenCentral()
                                }

                                dependencies {
                                    annotationProcessor("org.projectlombok:lombok:1.18.46")

                                    compileOnly("org.projectlombok:lombok:1.18.46")

                                    implementation("org.slf4j:slf4j-api:2.0.17")

                                    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.4")
                                    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.4")
                                }
                                """
                )
        );
    }

    @Test
    @DisplayName("catalog: seeds all four aliases and references libs.X in build.gradle.kts")
    void catalog_seedsAllAliasesAndReferences() {
        rewriteRun(
                toml(
                        """
                                [versions]

                                [libraries]
                                """,
                        """
                                [versions]
                                lombok = "1.18.46"
                                slf4j = "2.0.17"
                                log4j = "2.25.4"

                                [libraries]
                                lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }
                                slf4jApi = { module = "org.slf4j:slf4j-api", version.ref = "slf4j" }
                                log4jSlf4jImpl = { module = "org.apache.logging.log4j:log4j-slf4j2-impl", version.ref = "log4j" }
                                log4jCore = { module = "org.apache.logging.log4j:log4j-core", version.ref = "log4j" }
                                """,
                        spec -> spec.path("gradle/libs.versions.toml")
                ),
                buildGradleKts(
                        """
                                plugins {
                                    java
                                }
                                repositories {
                                    mavenCentral()
                                }
                                """,
                        """
                                plugins {
                                    java
                                }
                                repositories {
                                    mavenCentral()
                                }

                                dependencies {
                                    annotationProcessor(libs.lombok)

                                    compileOnly(libs.lombok)

                                    implementation(libs.slf4jApi)

                                    runtimeOnly(libs.log4jCore)
                                    runtimeOnly(libs.log4jSlf4jImpl)
                                }
                                """
                )
        );
    }
}
