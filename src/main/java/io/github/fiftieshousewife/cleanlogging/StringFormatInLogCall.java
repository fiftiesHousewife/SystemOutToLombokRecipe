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
 * Detects SLF4J calls of the form {@code log.info(String.format("user %s did %s", a, b))}
 * — a common anti-pattern that pre-formats the message string regardless of
 * whether the log level is enabled — and rewrites them to the parameterised
 * SLF4J equivalent {@code log.info("user {} did {}", a, b)}. The printf
 * specifiers are converted to {@code {}} placeholders via the same machinery
 * that powers {@link SystemOutToSlf4j}'s {@code printf} → SLF4J translation.
 *
 * <p>Receiver detection is structural (named {@code log}, the Lombok
 * {@code @Slf4j} convention) so the recipe is safe to compose after the
 * {@code @Slf4j}-adding recipes whose post-conversion calls don't carry
 * resolved SLF4J types.
 *
 * <p>The {@code String.format(Locale, format, args)} overload is intentionally
 * skipped — converting it would silently drop the explicit locale.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class StringFormatInLogCall extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replace String.format inside SLF4J log calls with parameterised SLF4J formatting";
    }

    @Override
    public String getDescription() {
        return "Rewrites `log.X(String.format(\"...%s...\", args))` to "
                + "`log.X(\"...{}...\", args)` so the message is only assembled when "
                + "the log level is enabled. Printf specifiers (%s/%d/%n etc.) are "
                + "converted to SLF4J `{}` placeholders. The "
                + "String.format(Locale, ...) overload is skipped.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("logging", "lombok", "slf4j", "performance");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new StringFormatInLogCallVisitor();
    }
}
