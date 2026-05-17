package io.github.fiftieshousewife.smoketest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One JUnit test per cell in the §2 smoke matrix: each top-level recipe in
 * the YAML, paired with the catalog axis (with/without toml) where the
 * recipe manages dependencies, or a single cell with pre-declared deps for
 * the {@code *NoDeps} variants. Each cell scaffolds a fresh /tmp project,
 * runs the recipe end-to-end via a nested Gradle process, and confirms the
 * post-rewrite Java compiles.
 *
 * <p>Slow (~20s/cell) — these are the pre-publish gate, not part of
 * {@code ./gradlew check}.
 */
@DisplayName("Pre-publish smoke tests: scaffold a project, run the recipe, compile the result")
class SmokeTest {

    private final SmokeTestConfig config = SmokeTestConfig.fromSystemProperties();

    static Stream<Arguments> matrix() {
        return Stream.of(
                cell(new SmokeVariant("SystemOutToSlf4jRecipe + no toml",
                        "io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipe",
                        SmokeVariant.CatalogMode.WITHOUT_TOML,
                        true,
                        List.of(Fixture.GREETING_SYSTEM_OUT))),
                cell(new SmokeVariant("SystemOutToSlf4jRecipe + with empty toml",
                        "io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipe",
                        SmokeVariant.CatalogMode.WITH_EMPTY_TOML,
                        true,
                        List.of(Fixture.GREETING_SYSTEM_OUT))),
                cell(new SmokeVariant("SystemOutToSlf4jRecipeNoDeps",
                        "io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipeNoDeps",
                        SmokeVariant.CatalogMode.NOT_APPLICABLE,
                        false,
                        List.of(Fixture.GREETING_SYSTEM_OUT))),
                cell(new SmokeVariant("ConvertManualLoggerToSlf4jRecipe + no toml",
                        "io.github.fiftieshousewife.cleanlogging.ConvertManualLoggerToSlf4jRecipe",
                        SmokeVariant.CatalogMode.WITHOUT_TOML,
                        true,
                        List.of(Fixture.ORDER_SERVICE_MANUAL_LOG4J2))),
                cell(new SmokeVariant("ConvertManualLoggerToSlf4jRecipe + with empty toml",
                        "io.github.fiftieshousewife.cleanlogging.ConvertManualLoggerToSlf4jRecipe",
                        SmokeVariant.CatalogMode.WITH_EMPTY_TOML,
                        true,
                        List.of(Fixture.ORDER_SERVICE_MANUAL_LOG4J2))),
                cell(new SmokeVariant("ConvertManualLoggerToSlf4jRecipeNoDeps",
                        "io.github.fiftieshousewife.cleanlogging.ConvertManualLoggerToSlf4jRecipeNoDeps",
                        SmokeVariant.CatalogMode.NOT_APPLICABLE,
                        false,
                        List.of(Fixture.ORDER_SERVICE_MANUAL_LOG4J2))),
                cell(new SmokeVariant("MigrateToSlf4jRecipe + no toml",
                        "io.github.fiftieshousewife.cleanlogging.MigrateToSlf4jRecipe",
                        SmokeVariant.CatalogMode.WITHOUT_TOML,
                        true,
                        List.of(Fixture.GREETING_SYSTEM_OUT, Fixture.ORDER_SERVICE_MANUAL_LOG4J2))),
                cell(new SmokeVariant("MigrateToSlf4jRecipe + with empty toml",
                        "io.github.fiftieshousewife.cleanlogging.MigrateToSlf4jRecipe",
                        SmokeVariant.CatalogMode.WITH_EMPTY_TOML,
                        true,
                        List.of(Fixture.GREETING_SYSTEM_OUT, Fixture.ORDER_SERVICE_MANUAL_LOG4J2))),
                cell(new SmokeVariant("MigrateToSlf4jRecipeNoDeps",
                        "io.github.fiftieshousewife.cleanlogging.MigrateToSlf4jRecipeNoDeps",
                        SmokeVariant.CatalogMode.NOT_APPLICABLE,
                        false,
                        List.of(Fixture.GREETING_SYSTEM_OUT, Fixture.ORDER_SERVICE_MANUAL_LOG4J2))),
                cell(new SmokeVariant("MigrateToCleanLoggingRecipe + no toml",
                        "io.github.fiftieshousewife.cleanlogging.MigrateToCleanLoggingRecipe",
                        SmokeVariant.CatalogMode.WITHOUT_TOML,
                        true,
                        List.of(Fixture.GREETING_SYSTEM_OUT, Fixture.ORDER_SERVICE_MANUAL_LOG4J2))),
                cell(new SmokeVariant("MigrateToCleanLoggingRecipe + with empty toml",
                        "io.github.fiftieshousewife.cleanlogging.MigrateToCleanLoggingRecipe",
                        SmokeVariant.CatalogMode.WITH_EMPTY_TOML,
                        true,
                        List.of(Fixture.GREETING_SYSTEM_OUT, Fixture.ORDER_SERVICE_MANUAL_LOG4J2))),
                cell(new SmokeVariant("MigrateToCleanLoggingRecipeNoDeps",
                        "io.github.fiftieshousewife.cleanlogging.MigrateToCleanLoggingRecipeNoDeps",
                        SmokeVariant.CatalogMode.NOT_APPLICABLE,
                        false,
                        List.of(Fixture.GREETING_SYSTEM_OUT, Fixture.ORDER_SERVICE_MANUAL_LOG4J2))),
                cell(new SmokeVariant("DirectSlf4jLoggerFieldToLombokRecipe + no toml",
                        "io.github.fiftieshousewife.cleanlogging.DirectSlf4jLoggerFieldToLombokRecipe",
                        SmokeVariant.CatalogMode.WITHOUT_TOML,
                        true,
                        List.of(Fixture.ORDER_SERVICE_SLF4J_DIRECT))),
                cell(new SmokeVariant("DirectSlf4jLoggerFieldToLombokRecipe + with empty toml",
                        "io.github.fiftieshousewife.cleanlogging.DirectSlf4jLoggerFieldToLombokRecipe",
                        SmokeVariant.CatalogMode.WITH_EMPTY_TOML,
                        true,
                        List.of(Fixture.ORDER_SERVICE_SLF4J_DIRECT))),
                cell(new SmokeVariant("DirectSlf4jLoggerFieldToLombokRecipeNoDeps",
                        "io.github.fiftieshousewife.cleanlogging.DirectSlf4jLoggerFieldToLombokRecipeNoDeps",
                        SmokeVariant.CatalogMode.NOT_APPLICABLE,
                        false,
                        List.of(Fixture.ORDER_SERVICE_SLF4J_DIRECT))),
                cell(new SmokeVariant("CommonsLoggingToSlf4jRecipe + no toml",
                        "io.github.fiftieshousewife.cleanlogging.CommonsLoggingToSlf4jRecipe",
                        SmokeVariant.CatalogMode.WITHOUT_TOML,
                        true,
                        List.of(Fixture.ORDER_SERVICE_COMMONS_LOGGING))),
                cell(new SmokeVariant("CommonsLoggingToSlf4jRecipe + with empty toml",
                        "io.github.fiftieshousewife.cleanlogging.CommonsLoggingToSlf4jRecipe",
                        SmokeVariant.CatalogMode.WITH_EMPTY_TOML,
                        true,
                        List.of(Fixture.ORDER_SERVICE_COMMONS_LOGGING))),
                cell(new SmokeVariant("CommonsLoggingToSlf4jRecipeNoDeps",
                        "io.github.fiftieshousewife.cleanlogging.CommonsLoggingToSlf4jRecipeNoDeps",
                        SmokeVariant.CatalogMode.NOT_APPLICABLE,
                        false,
                        List.of(Fixture.ORDER_SERVICE_COMMONS_LOGGING))),
                cell(new SmokeVariant("Log4j1ToSlf4jRecipe + no toml",
                        "io.github.fiftieshousewife.cleanlogging.Log4j1ToSlf4jRecipe",
                        SmokeVariant.CatalogMode.WITHOUT_TOML,
                        true,
                        List.of(Fixture.ORDER_SERVICE_LOG4J1))),
                cell(new SmokeVariant("Log4j1ToSlf4jRecipe + with empty toml",
                        "io.github.fiftieshousewife.cleanlogging.Log4j1ToSlf4jRecipe",
                        SmokeVariant.CatalogMode.WITH_EMPTY_TOML,
                        true,
                        List.of(Fixture.ORDER_SERVICE_LOG4J1))));
    }

    private static Arguments cell(final SmokeVariant variant) {
        return Arguments.of(variant.displayName(), variant);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void variantRewritesAndCompiles(final String displayName,
                                    final SmokeVariant variant,
                                    @TempDir final Path tempDir) {
        final Path projectDir = tempDir.resolve(variant.safeDirName());
        new SmokeProject(projectDir, variant, config).scaffold();

        final GradleRunner runner = new GradleRunner(projectDir, config);
        final GradleRunner.Result rewriteRun = runner.run("rewriteRun");
        assertThat(rewriteRun.succeeded())
                .as("rewriteRun should succeed for variant %s\nLog: %s\n%s",
                        displayName, rewriteRun.logFile(), rewriteRun.output())
                .isTrue();

        final GradleRunner.Result compileJava = runner.run("compileJava");
        assertThat(compileJava.succeeded())
                .as("compileJava should succeed after rewrite for variant %s\nLog: %s\n%s",
                        displayName, compileJava.logFile(), compileJava.output())
                .isTrue();
    }
}
