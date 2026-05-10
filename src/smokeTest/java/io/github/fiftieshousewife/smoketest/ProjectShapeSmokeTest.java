package io.github.fiftieshousewife.smoketest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the §2a project-shape matrix end-to-end. One JUnit test per template
 * (A–F): scaffold a /tmp project of the chosen topology + DSL, invoke
 * {@code rewriteRun} in each {@link ProjectShapeVariant#rewriteRunSubdirs() subdir}
 * (one for non-composite shapes, two for {@code includeBuild} composites),
 * then {@code compileJava} and confirm a {@code @Slf4j} annotation landed in
 * each Java fixture (otherwise the recipe was a silent no-op and {@code compileJava}
 * green is a false positive).
 */
@DisplayName("Pre-publish smoke tests: §2a project-shape matrix (multi-module / build-logic / composite)")
class ProjectShapeSmokeTest {

    private final SmokeTestConfig config = SmokeTestConfig.fromSystemProperties();

    static Stream<Arguments> matrix() {
        return Stream.of(
                cell(new ProjectShapeVariant(
                        "A — multi-module Kotlin DSL",
                        "A",
                        "io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipe",
                        ProjectShapeVariant.Topology.MULTI_MODULE,
                        ProjectShapeVariant.Dsl.KOTLIN,
                        List.of(""))),
                cell(new ProjectShapeVariant(
                        "B — multi-module Groovy DSL",
                        "B",
                        "io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipe",
                        ProjectShapeVariant.Topology.MULTI_MODULE,
                        ProjectShapeVariant.Dsl.GROOVY,
                        List.of(""))),
                cell(new ProjectShapeVariant(
                        "C — Kotlin with build-logic subproject (include)",
                        "C",
                        "io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipe",
                        ProjectShapeVariant.Topology.BUILD_LOGIC_INCLUDE,
                        ProjectShapeVariant.Dsl.KOTLIN,
                        List.of(""))),
                cell(new ProjectShapeVariant(
                        "D — Groovy with build-logic subproject (include)",
                        "D",
                        "io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipe",
                        ProjectShapeVariant.Topology.BUILD_LOGIC_INCLUDE,
                        ProjectShapeVariant.Dsl.GROOVY,
                        List.of(""))),
                cell(new ProjectShapeVariant(
                        "E — Kotlin composite build (includeBuild)",
                        "E",
                        "io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipe",
                        ProjectShapeVariant.Topology.COMPOSITE_INCLUDE_BUILD,
                        ProjectShapeVariant.Dsl.KOTLIN,
                        List.of("", "build-logic"))),
                cell(new ProjectShapeVariant(
                        "F — Groovy composite build (includeBuild)",
                        "F",
                        "io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipe",
                        ProjectShapeVariant.Topology.COMPOSITE_INCLUDE_BUILD,
                        ProjectShapeVariant.Dsl.GROOVY,
                        List.of("", "build-logic"))),
                cell(new ProjectShapeVariant(
                        "G — release-shaped consumer (existing Lombok + ben-manes plugin + multi-package sources)",
                        "G",
                        "io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipe",
                        ProjectShapeVariant.Topology.RELEASE_SHAPED_CONSUMER,
                        ProjectShapeVariant.Dsl.KOTLIN,
                        List.of(""))));
    }

    private static Arguments cell(final ProjectShapeVariant variant) {
        return Arguments.of(variant.displayName(), variant);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void shapeRewritesAndCompiles(final String displayName,
                                  final ProjectShapeVariant variant,
                                  @TempDir final Path tempDir) {
        final Path projectDir = tempDir.resolve(variant.safeDirName());
        new ProjectShapeScaffolder(projectDir, variant, config).scaffold();

        for (final String subdir : variant.rewriteRunSubdirs()) {
            final Path runDir = subdir.isEmpty() ? projectDir : projectDir.resolve(subdir);
            final GradleRunner.Result rewriteRun = new GradleRunner(runDir, config).run("rewriteRun");
            assertThat(rewriteRun.succeeded())
                    .as("rewriteRun should succeed for %s in %s\nLog: %s\n%s",
                            displayName, subdir.isEmpty() ? "<root>" : subdir,
                            rewriteRun.logFile(), rewriteRun.output())
                    .isTrue();
        }

        final GradleRunner.Result compileJava = new GradleRunner(projectDir, config).run("compileJava");
        assertThat(compileJava.succeeded())
                .as("compileJava should succeed after rewrite for %s\nLog: %s\n%s",
                        displayName, compileJava.logFile(), compileJava.output())
                .isTrue();

        assertSlf4jApplied(projectDir, displayName);
    }

    private static void assertSlf4jApplied(final Path projectDir, final String displayName) {
        try (final var stream = Files.walk(projectDir)) {
            final List<Path> javaSources = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/build/"))
                    .toList();
            assertThat(javaSources)
                    .as("expected at least one Java fixture under %s", projectDir)
                    .isNotEmpty();
            for (final Path java : javaSources) {
                final String content = Files.readString(java);
                assertThat(content)
                        .as("recipe should have added @Slf4j to %s for %s — silent no-op otherwise",
                                projectDir.relativize(java), displayName)
                        .contains("@Slf4j");
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to verify @Slf4j application under " + projectDir, e);
        }
    }
}
