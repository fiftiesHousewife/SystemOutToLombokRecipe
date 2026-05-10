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
 * Drops explicit {@code .toString()} calls on SLF4J substitution arguments.
 * SLF4J's parameterised API already calls {@code toString()} on each
 * non-string substitution lazily — only when the log level is enabled — so an
 * explicit call defeats the purpose of the parameterised form by formatting
 * the value unconditionally:
 *
 * <pre>
 *   log.info("x = {}", obj.toString())   // unconditional toString, then maybe used
 *   log.info("x = {}", obj)              // toString only when info is enabled
 * </pre>
 *
 * <p>The recipe runs across all substitution arguments (index 1 and beyond)
 * of any {@code log.X(...)} call and replaces {@code <expr>.toString()} with
 * {@code <expr>}. Receiver detection is structural (named {@code log},
 * the Lombok {@code @Slf4j} convention).
 *
 * <p>Pairs naturally with {@link Slf4jConcatToParameterized}: that recipe
 * peels {@code log.info("x = " + obj.toString())} into
 * {@code log.info("x = {}", obj.toString())}, after which this one drops
 * the explicit call.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class ExplicitToStringInLogCall extends Recipe {

    @Override
    public String getDisplayName() {
        return "Drop explicit .toString() on SLF4J substitution arguments";
    }

    @Override
    public String getDescription() {
        return "SLF4J's parameterised API calls toString() on each non-string substitution "
                + "argument lazily, so an explicit toString() in the call site defeats the "
                + "point of the parameterised form by formatting the value unconditionally. "
                + "Drops .toString() on each substitution arg of any log.X(...) call.";
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
        return new ExplicitToStringInLogCallVisitor();
    }
}
