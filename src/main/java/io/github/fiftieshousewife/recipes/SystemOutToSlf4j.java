package io.github.fiftieshousewife.recipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Converts {@code System.out} and {@code System.err} print calls to Lombok
 * {@code log.xxx(...)} statements. Assumes the class has already been annotated
 * with {@code @Slf4j} (apply {@link AddLombokSlf4jAnnotation} first).
 *
 * <p>When {@code requireLombokOnClasspath} is set, the rewrite is skipped in
 * source files whose classpath doesn't contain {@code lombok.extern.slf4j.Slf4j}
 * — without {@code @Slf4j} there is no {@code log} field, so rewriting the
 * {@code System.out} call would produce uncompilable Java.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class SystemOutToSlf4j extends Recipe {

    private static final String SYSTEM_OUT = "System.out";
    private static final String SYSTEM_ERR = "System.err";

    @Option(displayName = "Require Lombok on classpath",
            description = "When true, only rewrite System.out calls in source files where " +
                    "lombok.extern.slf4j.Slf4j is resolvable on the classpath.",
            required = false)
    boolean requireLombokOnClasspath;

    @Override
    public String getDisplayName() {
        return "Replace System.out with Lombok log statements";
    }

    @Override
    public String getDescription() {
        return "Replaces System.out.println(), System.out.print(), and System.out.printf() calls "
                + "with appropriate log.info() statements using parameterized logging. "
                + "Also converts System.err calls to log.error().";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("logging", "lombok", "slf4j");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>("java.io.PrintStream println(..)"),
                        new UsesMethod<>("java.io.PrintStream print(..)"),
                        new UsesMethod<>("java.io.PrintStream printf(..)")),
                new SystemOutVisitor(requireLombokOnClasspath));
    }

    static boolean isSystemOutOrErr(final J.MethodInvocation method) {
        final Expression select = method.getSelect();
        return select != null && (SYSTEM_OUT.equals(select.toString()) || SYSTEM_ERR.equals(select.toString()));
    }

    static boolean isSystemErr(final J.MethodInvocation method) {
        final Expression select = method.getSelect();
        return select != null && SYSTEM_ERR.equals(select.toString());
    }

    static boolean hasNoRealArg(final List<Expression> args) {
        return args.isEmpty() || (args.size() == 1 && args.get(0) instanceof J.Empty);
    }
}
