package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

@NullMarked
final class CommonsFatalRenameVisitor extends JavaIsoVisitor<Integer> {

    private static final MethodMatcher FATAL =
            new MethodMatcher("org.apache.commons.logging.Log fatal(..)");

    private static final MethodMatcher IS_FATAL_ENABLED =
            new MethodMatcher("org.apache.commons.logging.Log isFatalEnabled()");

    @Override
    public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final Integer p) {
        final J.MethodInvocation visited = super.visitMethodInvocation(method, p);
        if (FATAL.matches(visited)) {
            return visited.withName(visited.getName().withSimpleName("error"));
        }
        if (IS_FATAL_ENABLED.matches(visited)) {
            return visited.withName(visited.getName().withSimpleName("isErrorEnabled"));
        }
        return visited;
    }
}
