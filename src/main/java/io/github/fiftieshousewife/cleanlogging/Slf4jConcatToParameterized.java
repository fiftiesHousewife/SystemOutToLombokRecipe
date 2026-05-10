package io.github.fiftieshousewife.cleanlogging;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import java.time.Duration;
import java.util.Set;

/**
 * Parameterizes {@code log.X("msg " + a + " " + b)} → {@code log.X("msg {} {}", a, b)}
 * for SLF4J level methods (trace/debug/info/warn/error). Fires on receiver
 * named {@code log} (Lombok's {@code @Slf4j} convention), so it's safe to chain
 * after the {@code @Slf4j}-adding recipes in {@code MigrateToSlf4jRecipe}'s
 * pipeline. Only single-argument calls are touched — calls already passing
 * a throwable as a second argument are left to the upstream concat-message
 * recipe.
 *
 * <p>Why hand-rolled instead of upstream's
 * {@code org.openrewrite.java.logging.ParameterizedLogging}: upstream's
 * {@code UsesMethod} precondition matches by bound method type. After our
 * recipes add {@code @Slf4j} and drop the prior logger field, the
 * {@code log.info(...)} method invocations in the LST still carry the
 * pre-conversion logger type since OpenRewrite doesn't re-parse the LST
 * mid-pipeline — so upstream's precondition skips them.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class Slf4jConcatToParameterized extends Recipe {

    @Override
    public String getDisplayName() {
        return "Parameterize concatenated SLF4J log messages";
    }

    @Override
    public String getDescription() {
        return "Rewrites `log.X(\"msg \" + a + \" \" + b)` to `log.X(\"msg {} {}\", a, b)` for SLF4J level "
                + "methods. Only fires on the Lombok-generated `log` receiver, so it can safely run after the "
                + "@Slf4j-adding recipes without risking unrelated log frameworks.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("logging", "lombok", "slf4j", "performance");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new Slf4jConcatToParameterizedVisitor();
    }
}
