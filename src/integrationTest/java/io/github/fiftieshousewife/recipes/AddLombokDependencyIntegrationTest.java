package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.toml.Assertions.toml;

@DisplayName("AddLombokDependency end-to-end with real GradleProject marker")
class AddLombokDependencyIntegrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi())
                .recipe(Environment.builder()
                        .scanRuntimeClasspath("io.github.fiftieshousewife")
                        .build()
                        .activateRecipes("io.github.fiftieshousewife.AddLombokDependency"));
    }

    @Test
    @DisplayName("inline (no catalog): adds compileOnly + annotationProcessor declarations")
    void inline_addsLombokDeclarations() {
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
                                    annotationProcessor("org.projectlombok:lombok:1.18.44")

                                    compileOnly("org.projectlombok:lombok:1.18.44")
                                }
                                """
                )
        );
    }

    @Test
    @DisplayName("catalog: seeds libs.versions.toml and uses libs.lombok in build.gradle.kts")
    void catalog_seedsAndReferences() {
        rewriteRun(
                toml(
                        """
                                [versions]

                                [libraries]
                                """,
                        """
                                [versions]
                                lombok = "1.18.44"

                                [libraries]
                                lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }
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
                                }
                                """
                )
        );
    }
}
