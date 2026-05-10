package io.github.fiftieshousewife.cleanlogging;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static io.github.fiftieshousewife.cleanlogging.LoggerNames.SLF4J_LOGGER;

/**
 * Converts a class with a directly-declared {@code static final} SLF4J
 * {@code Logger} field initialised via {@code LoggerFactory.getLogger(...)}
 * into the Lombok {@code @Slf4j} form: drops the field, adds the annotation,
 * and renames references to the old field to {@code log}. Removes the
 * {@code org.slf4j.Logger} / {@code LoggerFactory} imports once they're
 * unused.
 *
 * <p>Skips classes that already carry any Lombok logging annotation, classes
 * without exactly one eligible field, and (when
 * {@code requireLombokOnClasspath} is set) source files whose classpath
 * doesn't actually contain {@code lombok.extern.slf4j.Slf4j}.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class DirectSlf4jLoggerFieldToLombok extends Recipe {

    private static final MethodMatcher LOGGER_FACTORY_GET_LOGGER =
            new MethodMatcher("org.slf4j.LoggerFactory getLogger(..)");

    @Option(displayName = "Require Lombok on classpath",
            description = "When true, only convert direct SLF4J Logger fields in source files where " +
                    "lombok.extern.slf4j.Slf4j is resolvable on the classpath. " +
                    "Use this in setups that don't add Lombok as part of the recipe run " +
                    "(e.g. multi-module projects where Lombok lives on only some modules).",
            required = false)
    boolean requireLombokOnClasspath;

    @Override
    public String getDisplayName() {
        return "Convert directly-declared SLF4J Logger fields to Lombok @Slf4j";
    }

    @Override
    public String getDescription() {
        return "Finds classes that declare a `private` or package-private `static final Logger` field "
                + "initialised from `LoggerFactory.getLogger(...)`, replaces them with Lombok's `@Slf4j` "
                + "annotation, and renames references to the old field so they use the Lombok-generated `log`.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("logging", "lombok", "slf4j");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(3);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(SLF4J_LOGGER.fqn(), false),
                new DirectSlf4jLoggerFieldToLombokVisitor(requireLombokOnClasspath));
    }

    static Optional<LoggerField> findDirectSlf4jField(final J.ClassDeclaration classDecl) {
        return LoggerFieldFinders.findExactlyOneEligibleField(
                classDecl, SLF4J_LOGGER.fqn(), LOGGER_FACTORY_GET_LOGGER);
    }
}
