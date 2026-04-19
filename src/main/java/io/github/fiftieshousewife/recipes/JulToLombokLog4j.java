package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts {@code java.util.logging.Logger} level-named method calls to the
 * {@code log.xxx(...)} methods exposed by Lombok's {@code @Log4j2} annotation.
 * Assumes the class has already been annotated with {@code @Log4j2} — run
 * {@link AddLombokLog4j2Annotation} first.
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
 * <p>Leaves the original {@code Logger} field declaration in place — remove it
 * (plus the {@code java.util.logging.Logger} import) after running this recipe.
 */
@NullMarked
public class JulToLombokLog4j extends Recipe {

    private static final Map<String, MethodMatcher> MATCHERS = Map.of(
            "severe", matcher("severe"),
            "warning", matcher("warning"),
            "info", matcher("info"),
            "config", matcher("config"),
            "fine", matcher("fine"),
            "finer", matcher("finer"),
            "finest", matcher("finest"));

    private static final Map<String, String> JUL_TO_LOG4J = Map.of(
            "severe", "error",
            "warning", "warn",
            "info", "info",
            "config", "debug",
            "fine", "debug",
            "finer", "trace",
            "finest", "trace");

    private static MethodMatcher matcher(final String methodName) {
        return new MethodMatcher("java.util.logging.Logger " + methodName + "(..)");
    }

    @Override
    public String getDisplayName() {
        return "Replace java.util.logging calls with Lombok log statements";
    }

    @Override
    public String getDescription() {
        return "Converts java.util.logging.Logger level-named method calls "
                + "(severe/warning/info/config/fine/finer/finest) to the equivalent "
                + "Lombok @Log4j2 log methods (error/warn/info/debug/trace).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method,
                                                            final ExecutionContext ctx) {
                final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                final Optional<String> level = julLevelOf(visited);
                if (level.isEmpty()) {
                    return visited;
                }
                final List<Expression> args = visited.getArguments();
                if (args.size() != 1) {
                    return visited;
                }
                final String targetMethod = JUL_TO_LOG4J.get(level.get());
                return JavaTemplate.builder("log." + targetMethod + "(#{any()})")
                        .build()
                        .apply(getCursor(), visited.getCoordinates().replace(), args.get(0));
            }
        };
    }

    static Optional<String> julLevelOf(final J.MethodInvocation method) {
        return MATCHERS.entrySet().stream()
                .filter(entry -> entry.getValue().matches(method))
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
