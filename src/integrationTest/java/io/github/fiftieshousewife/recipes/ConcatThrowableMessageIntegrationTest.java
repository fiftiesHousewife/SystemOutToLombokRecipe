package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;

@DisplayName("ConcatThrowableMessage end-to-end with real GradleProject marker")
class ConcatThrowableMessageIntegrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi())
                .recipe(new ConcatThrowableMessage());
    }

    @Test
    @DisplayName("real Maven Central resolution: peels e.getMessage() into a separate argument")
    void peelsConcatThrowableMessageWithRealClasspath() {
        rewriteRun(
                java(
                        """
                                package com.example;

                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    public void run(Exception e) {
                                        log.error("failed: " + e.getMessage());
                                    }
                                }
                                """,
                        """
                                package com.example;

                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    public void run(Exception e) {
                                        log.error("failed: ", e);
                                    }
                                }
                                """
                ),
                buildGradleKts(
                        """
                                plugins {
                                    java
                                }
                                repositories {
                                    mavenCentral()
                                }
                                dependencies {
                                    implementation("org.slf4j:slf4j-api:2.0.17")
                                }
                                """
                )
        );
    }
}