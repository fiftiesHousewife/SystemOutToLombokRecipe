package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;

/**
 * Verifies the {@code requireLombokOnClasspath} gate's safe-default behaviour
 * against a real-resolved {@code GradleProject} marker. The gate looks for a
 * {@link org.openrewrite.java.marker.JavaSourceSet} marker on the compilation
 * unit and treats its absence (or absence of {@code lombok.extern.slf4j.Slf4j}
 * on its classpath) as "Lombok unavailable" — so the gated annotation-add
 * skips rather than drop an {@code @Slf4j} that won't compile.
 *
 * <p>Scoped to {@code AddLombokSlf4jAnnotation} alone, not the
 * {@code JavaTransformsClasspathGated} composition. The composition has a
 * separate hazard (the call-conversion recipes are NOT gated, so in a
 * Lombok-less module they still rewrite {@code System.out.println(...)} to
 * {@code log.info(...)} producing uncompilable Java) — see BACKLOG.
 *
 * <p>The positive-case (Lombok IS on the classpath) is covered by
 * {@link io.github.fiftieshousewife.recipes.LombokClasspathGateTest} at
 * unit-test level via a hand-rolled {@code JavaSourceSet} marker.
 */
@DisplayName("Classpath gate (AddLombokSlf4jAnnotation) with real GradleProject marker")
class ClasspathGateIntegrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi())
                .recipe(new AddLombokSlf4jAnnotation(true));
    }

    @Test
    @DisplayName("no Lombok on classpath: AddLombokSlf4jAnnotation skips the class")
    void gateSkipsWhenJavaSourceSetMarkerAbsent() {
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
