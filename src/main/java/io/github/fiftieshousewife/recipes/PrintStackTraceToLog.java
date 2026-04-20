package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

/**
 * Replaces {@code exception.printStackTrace()} calls with {@code log.error(...)} statements.
 * Assumes the class has been annotated with {@code @Slf4j} (apply
 * {@link AddLombokSlf4jAnnotation} first).
 */
@NullMarked
public class PrintStackTraceToLog extends Recipe {

    private static final MethodMatcher PRINT_STACK_TRACE = new MethodMatcher("java.lang.Throwable printStackTrace(..)");

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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method,
                                                            final ExecutionContext ctx) {
                final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                if (!PRINT_STACK_TRACE.matches(visited) || visited.getSelect() == null) {
                    return visited;
                }
                return JavaTemplate.builder("log.error(\"Exception occurred\", #{any()})")
                        .build()
                        .apply(getCursor(), visited.getCoordinates().replace(), visited.getSelect());
            }
        };
    }
}
