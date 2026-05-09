package io.github.fiftieshousewife.recipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static io.github.fiftieshousewife.recipes.LoggerNames.LOG4J2_LOGGER;

/**
 * Converts classes that hand-roll a Log4j2 logger field into the Lombok
 * {@code @Slf4j} form. For a class like
 * <pre>
 *   public class Foo {
 *       private static final Logger logger = LogManager.getLogger(Foo.class);
 *       void m() { logger.info("hi"); }
 *   }
 * </pre>
 * produces
 * <pre>
 *   &#064;Log4j2
 *   public class Foo {
 *       void m() { log.info("hi"); }
 *   }
 * </pre>
 * Renames usages of the old field to {@code log} to match the Lombok-generated
 * field. Removes the {@code org.apache.logging.log4j.Logger} and
 * {@code LogManager} imports when they are no longer used.
 *
 * <p>When {@code requireLombokOnClasspath} is set, the conversion is skipped
 * for source files whose classpath doesn't contain
 * {@code lombok.extern.slf4j.Slf4j}.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class ConvertManualLoggerToSlf4j extends Recipe {

    @Option(displayName = "Require Lombok on classpath",
            description = "When true, only convert manual loggers in source files where " +
                    "lombok.extern.slf4j.Slf4j is resolvable on the classpath.",
            required = false)
    boolean requireLombokOnClasspath;

    @Override
    public String getDisplayName() {
        return "Convert manually declared Log4j2 logger fields to Lombok @Slf4j";
    }

    @Override
    public String getDescription() {
        return "Finds classes that declare a `private static final Logger` field initialised from "
                + "`LogManager.getLogger(...)`, replaces them with Lombok's `@Slf4j` annotation, "
                + "and renames references to the old field so they use the Lombok-generated `log`.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("logging", "lombok", "slf4j", "log4j2");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(3);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(LOG4J2_LOGGER.fqn(), false),
                new ConvertManualLoggerToSlf4jVisitor(requireLombokOnClasspath));
    }

    static Optional<LoggerField> findManualLog4j2Field(final J.ClassDeclaration classDecl) {
        return LoggerFieldFinders.findFirstSingleVariableFieldOfType(classDecl, LOG4J2_LOGGER.fqn());
    }
}
