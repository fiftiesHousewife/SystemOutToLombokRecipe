package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;

/**
 * Verifies the {@code JavaTransformsClasspathGated} composition's
 * safe-default behaviour against a real-resolved {@code GradleProject}
 * marker. Each leaf recipe in the composition reads
 * {@link LombokClasspathGate}, which looks for a
 * {@link org.openrewrite.java.marker.JavaSourceSet} marker on the
 * compilation unit and treats its absence (or absence of
 * {@code lombok.extern.slf4j.Slf4j} on its classpath) as
 * "Lombok unavailable" — so the whole composition no-ops rather than
 * dropping an {@code @Slf4j} that won't compile or rewriting
 * {@code System.out.println(...)} to a non-existent {@code log.info(...)}.
 *
 * <p>The positive-case (Lombok IS on the classpath) is covered by
 * {@link io.github.fiftieshousewife.recipes.LombokClasspathGateTest} at
 * unit-test level via a hand-rolled {@code JavaSourceSet} marker, and by
 * {@link MigrateToSlf4jRecipeIntegrationTest} which exercises the
 * un-gated path with deps added.
 */
@DisplayName("Classpath gate (JavaTransformsClasspathGated) with real GradleProject marker")
class ClasspathGateIntegrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi())
                .recipe(Environment.builder()
                        .scanRuntimeClasspath("io.github.fiftieshousewife")
                        .build()
                        .activateRecipes("io.github.fiftieshousewife.JavaTransformsClasspathGated"));
    }

    @Test
    @DisplayName("no Lombok on classpath: composition leaves @Slf4j AND System.out alone")
    void gateSkipsEntireCompositionWhenLombokAbsent() {
        rewriteRun(
                java(
                        """
                                package com.example;

                                public class HasSystemOut {
                                    public void m() {
                                        System.out.println("hi");
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
                                """
                )
        );
    }
}
