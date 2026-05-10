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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static io.github.fiftieshousewife.cleanlogging.LoggerNames.JUL_LOGGER;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Converts {@code java.util.logging.Logger} level-named method calls to the
 * {@code log.xxx(...)} methods exposed by Lombok's {@code @Slf4j} annotation,
 * and cleans up the now-dead JUL plumbing. Assumes the class has already been
 * annotated with {@code @Slf4j} — run {@link AddLombokSlf4jAnnotation} first.
 *
 * <p>Level mappings:
 * <pre>
 *   logger.severe(msg)  → log.error(msg)
 *   logger.warning(msg) → log.warn(msg)
 *   logger.info(msg)    → log.info(msg)
 *   logger.config(msg)  → log.debug(msg)
 *   logger.fine(msg)    → log.debug(msg)
 *   logger.finer(msg)   → log.trace(msg)
 *   logger.finest(msg)  → log.trace(msg)
 * </pre>
 *
 * <p>After the call conversion, if the class had a
 * {@code private static final Logger logger = Logger.getLogger(...);} field
 * and nothing else in the class still references it, the field and its
 * {@code java.util.logging.Logger} import are removed.
 *
 * <p>When {@code requireLombokOnClasspath} is set, the rewrite is skipped in
 * source files whose classpath doesn't contain {@code lombok.extern.slf4j.Slf4j}.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class JulToSlf4j extends Recipe {

    private static final String MATCHER_TEMPLATE = "%s %s(..)";

    private static final Map<JulLevel, MethodMatcher> MATCHERS = Stream.of(JulLevel.values())
            .collect(toUnmodifiableMap(identity(), level -> matcher(level.julMethod())));

    static final MethodMatcher IS_LOGGABLE =
            new MethodMatcher("java.util.logging.Logger isLoggable(java.util.logging.Level)");

    private static MethodMatcher matcher(final String methodName) {
        return new MethodMatcher(MATCHER_TEMPLATE.formatted(JUL_LOGGER.fqn(), methodName));
    }

    @Option(displayName = "Require Lombok on classpath",
            description = "When true, only rewrite JUL calls in source files where " +
                    "lombok.extern.slf4j.Slf4j is resolvable on the classpath.",
            required = false)
    boolean requireLombokOnClasspath;

    @Override
    public String getDisplayName() {
        return "Replace java.util.logging calls with Lombok log statements";
    }

    @Override
    public String getDescription() {
        return "Converts java.util.logging.Logger level-named method calls "
                + "(severe/warning/info/config/fine/finer/finest) to the equivalent "
                + "Lombok @Slf4j log methods (error/warn/info/debug/trace), then "
                + "removes the JUL Logger field and its import when no other references remain.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("logging", "lombok", "slf4j", "jul");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(JUL_LOGGER.fqn(), false), new JulToSlf4jVisitor(requireLombokOnClasspath));
    }

    static Optional<JulLevel> julLevelOf(final J.MethodInvocation method) {
        return MATCHERS.entrySet().stream()
                .filter(entry -> entry.getValue().matches(method))
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
