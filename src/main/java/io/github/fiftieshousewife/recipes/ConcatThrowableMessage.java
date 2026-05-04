package io.github.fiftieshousewife.recipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesType;

import java.time.Duration;
import java.util.Set;

/**
 * Rewrites SLF4J log calls of the form
 * {@code log.error("failed: " + e.getMessage())} into
 * {@code log.error("failed: ", e)} — peels the trailing
 * {@code + e.getMessage()} off the message string and passes the
 * throwable as a separate argument so SLF4J can append the stack trace.
 *
 * <p>Multi-part left sides are preserved as-is: the binary chain
 * {@code "a " + b + ": " + e.getMessage()} becomes
 * {@code log.error("a " + b + ": ", e)}. The leading text is never
 * trimmed — leaving punctuation intact is safer than guessing what
 * the user meant.
 *
 * <p>Skips calls that already have more than one argument (those are
 * already either parameterized or correctly passing a Throwable),
 * calls whose right-hand side isn't {@code Throwable.getMessage()},
 * and calls on receivers that aren't an SLF4J {@code Logger}.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class ConcatThrowableMessage extends Recipe {

    @Override
    public String getDisplayName() {
        return "Move concatenated Throwable.getMessage() to a logger argument";
    }

    @Override
    public String getDescription() {
        return "Rewrites SLF4J log calls of the form `log.error(\"failed: \" + e.getMessage())` "
                + "into `log.error(\"failed: \", e)`. Peels the trailing `+ e.getMessage()` off "
                + "the message and passes the throwable as a separate argument so SLF4J can "
                + "append the stack trace. Multi-part LHS chains (`\"a \" + b + \": \" + "
                + "e.getMessage()`) are preserved verbatim.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("logging", "slf4j");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.slf4j.Logger", false),
                new ConcatThrowableMessageVisitor());
    }
}