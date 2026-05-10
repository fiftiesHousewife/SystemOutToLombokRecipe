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
 * Detects SLF4J calls where a {@link Throwable} is consumed by a placeholder
 * in the message format ({@code log.error("failed: {}", e)}) instead of
 * appended as the trailing throwable argument ({@code log.error("failed", e)}).
 * SLF4J binds the trailing throwable to the stack-trace slot only when the
 * placeholder count is one less than the substitution-argument count — when
 * the counts match, the throwable is substituted via {@code toString()} and
 * the stack trace is silently lost.
 *
 * <p>Fixes the bug by dropping the trailing placeholder, after which the
 * throwable lands on the stack-trace slot and gets logged in full. Receiver
 * detection is structural (named {@code log}, the Lombok {@code @Slf4j}
 * convention) so the recipe is safe to compose after the {@code @Slf4j}-adding
 * recipes whose post-conversion calls don't carry resolved SLF4J types.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class ThrowableLastArgumentNoPlaceholder extends Recipe {

    @Override
    public String getDisplayName() {
        return "Drop placeholder when SLF4J Throwable argument should be the trailing stack-trace slot";
    }

    @Override
    public String getDescription() {
        return "When the placeholder count in an SLF4J log message matches the substitution-argument count "
                + "and the last argument is a Throwable, SLF4J substitutes the throwable via toString() and "
                + "loses the stack trace. Drops the trailing `{}` so the throwable lands on the trailing "
                + "stack-trace slot instead.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("logging", "lombok", "slf4j", "RSPEC-S2629");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ThrowableLastArgumentNoPlaceholderVisitor();
    }
}
