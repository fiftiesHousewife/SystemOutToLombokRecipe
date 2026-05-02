package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;

@DisplayName("AddSlf4jDependencies end-to-end with real GradleProject marker")
class AddSlf4jDependenciesIntegrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi())
                .recipe(Environment.builder()
                        .scanRuntimeClasspath("io.github.fiftieshousewife")
                        .build()
                        .activateRecipes("io.github.fiftieshousewife.AddSlf4jDependencies"));
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
                                    annotationProcessor("org.projectlombok:lombok:1.18.44")

                                    compileOnly("org.projectlombok:lombok:1.18.44")

                                    implementation("org.slf4j:slf4j-api:2.0.17")

                                    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.4")
                                    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.4")
                                }
                                """
                )
        );
    }
}
