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

import static io.github.fiftieshousewife.cleanlogging.LoggerNames.COMMONS_LOG;

/**
 * Converts an Apache Commons Logging {@code Log} field
 * ({@code static final Log log = LogFactory.getLog(...)}) into the Lombok
 * {@code @Slf4j} form: drops the field, adds the annotation, renames
 * references, and rewrites {@code fatal}/{@code isFatalEnabled} to
 * {@code error}/{@code isErrorEnabled} (SLF4J has no fatal level). Other
 * Commons Logging level methods are name-compatible with SLF4J and pass
 * through. Imports are pruned once unused.
 *
 * <p>Skips already-Lombok-annotated classes, classes without exactly one
 * eligible field, and (with {@code requireLombokOnClasspath}) source files
 * whose classpath lacks {@code lombok.extern.slf4j.Slf4j}.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class CommonsLoggingToSlf4j extends Recipe {

    private static final MethodMatcher LOG_FACTORY_GET_LOG =
            new MethodMatcher("org.apache.commons.logging.LogFactory getLog(..)");

    @Option(displayName = "Require Lombok on classpath",
            description = "When true, only convert Commons Logging Log fields in source files where " +
                    "lombok.extern.slf4j.Slf4j is resolvable on the classpath. " +
                    "Use this in setups that don't add Lombok as part of the recipe run " +
                    "(e.g. multi-module projects where Lombok lives on only some modules).",
            required = false)
    boolean requireLombokOnClasspath;

    @Override
    public String getDisplayName() {
        return "Convert Apache Commons Logging to Lombok @Slf4j";
    }

    @Override
    public String getDescription() {
        return "Finds classes that declare a `private` or package-private `static final Log` field initialised "
                + "from `LogFactory.getLog(...)`, replaces them with Lombok's `@Slf4j`, renames the field "
                + "references, and rewrites `fatal`/`isFatalEnabled` calls to `error`/`isErrorEnabled`.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("logging", "lombok", "slf4j", "commons-logging");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(3);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(COMMONS_LOG.fqn(), false),
                new CommonsLoggingToSlf4jVisitor(requireLombokOnClasspath));
    }

    static Optional<LoggerField> findCommonsLogField(final J.ClassDeclaration classDecl) {
        return LoggerFieldFinders.findExactlyOneEligibleField(
                classDecl, COMMONS_LOG.fqn(), LOG_FACTORY_GET_LOG);
    }
}
