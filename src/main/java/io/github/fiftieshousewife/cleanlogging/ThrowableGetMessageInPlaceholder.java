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
 * Detects SLF4J calls of the form {@code log.error("oops: {}", e.getMessage())}
 * where the user has explicitly placed the throwable's message in a placeholder
 * but never passed the throwable itself as a trailing argument — so SLF4J has
 * nothing to bind to the stack-trace slot and the trace is silently lost.
 *
 * <p>Fixes the bug by appending the throwable as a new trailing argument:
 * {@code log.error("oops: {}", e.getMessage(), e)}. The original message
 * format and the explicit {@code getMessage()} call are preserved — SLF4J
 * substitutes {@code e.getMessage()} into the placeholder and binds {@code e}
 * to the trailing-throwable slot, so the log line reads "oops: &lt;message&gt;"
 * followed by the stack trace.
 *
 * <p>Receiver detection is structural (named {@code log}, the Lombok
 * {@code @Slf4j} convention) so the recipe is safe to compose after the
 * {@code @Slf4j}-adding recipes whose post-conversion calls don't carry
 * resolved SLF4J types.
 *
 * <p>Sibling of {@link ThrowableLastArgumentNoPlaceholder} (handles the
 * inverse case where the last arg is the throwable itself, consumed via
 * {@code toString()}) and {@link ConcatThrowableMessage} (handles the
 * concatenation form {@code log.error("failed: " + e.getMessage())}).
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class ThrowableGetMessageInPlaceholder extends Recipe {

    @Override
    public String getDisplayName() {
        return "Append throwable to SLF4J calls that pass only e.getMessage() in a placeholder";
    }

    @Override
    public String getDescription() {
        return "When an SLF4J log call passes a throwable's getMessage() as a substitution argument "
                + "but does not pass the throwable itself as a trailing argument, the stack trace is "
                + "silently lost. Appends the throwable as a new trailing argument so SLF4J binds it "
                + "to the stack-trace slot while still substituting the explicit message text into "
                + "the placeholder.";
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
        return new ThrowableGetMessageInPlaceholderVisitor();
    }
}
