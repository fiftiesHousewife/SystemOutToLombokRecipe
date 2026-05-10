package io.github.fiftieshousewife.cleanlogging;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static io.github.fiftieshousewife.cleanlogging.LombokClasspathGate.isAvailable;

/**
 * Replaces {@code exception.printStackTrace()} calls with {@code log.error(...)} statements.
 * Assumes the class has been annotated with {@code @Slf4j} (apply
 * {@link AddLombokSlf4jAnnotation} first).
 *
 * <p>The {@code printStackTrace(System.err)} and {@code printStackTrace(System.out)}
 * overloads are matched too; the stream argument is dropped — the rewritten
 * {@code log.error("Exception occurred", e)} routes to stderr via the level alone
 * (production {@code log4j2.xml} maps {@code ERROR} to {@code SYSTEM_ERR}).
 *
 * <p>When {@code requireLombokOnClasspath} is set, the rewrite is skipped in
 * source files whose classpath doesn't contain {@code lombok.extern.slf4j.Slf4j}
 * — without {@code @Slf4j} there is no {@code log} field for the rewrite to land on.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class PrintStackTraceToLog extends Recipe {

    private static final MethodMatcher PRINT_STACK_TRACE = new MethodMatcher("java.lang.Throwable printStackTrace(..)");
    private static final String LOG_ERROR_TEMPLATE = "log.error(\"Exception occurred\", #{any()})";

    @Option(displayName = "Require Lombok on classpath",
            description = "When true, only rewrite printStackTrace calls in source files where " +
                    "lombok.extern.slf4j.Slf4j is resolvable on the classpath.",
            required = false)
    boolean requireLombokOnClasspath;

    @Override
    public String getDisplayName() {
        return "Replace printStackTrace with log.error";
    }

    @Override
    public String getDescription() {
        return "Replaces exception.printStackTrace() calls with log.error() statements that properly log the exception. " +
                "Assumes the class has been annotated with @Slf4j by AddLombokSlf4jAnnotation recipe.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("logging", "lombok", "slf4j");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>("java.lang.Throwable printStackTrace(..)"), new JavaIsoVisitor<>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method,
                                                            final ExecutionContext ctx) {
                final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                return exceptionExpressionIfRewritable(visited)
                        .map(exception -> rewriteAsLogError(visited, exception))
                        .orElse(visited);
            }

            private Optional<Expression> exceptionExpressionIfRewritable(final J.MethodInvocation method) {
                return Optional.of(method)
                        .filter(PRINT_STACK_TRACE::matches)
                        .filter(m -> !requireLombokOnClasspath || isAvailable(getCursor()))
                        .map(J.MethodInvocation::getSelect);
            }

            private J.MethodInvocation rewriteAsLogError(final J.MethodInvocation original,
                                                         final Expression exception) {
                return JavaTemplate.builder(LOG_ERROR_TEMPLATE)
                        .build()
                        .apply(getCursor(), original.getCoordinates().replace(), exception);
            }
        });
    }
}
