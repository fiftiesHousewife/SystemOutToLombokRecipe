package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.Space;

import java.util.List;
import java.util.Optional;

@NullMarked
class ConcatThrowableMessageVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final List<MethodMatcher> SLF4J_LOG_MATCHERS = List.of(
            new MethodMatcher("org.slf4j.Logger trace(..)"),
            new MethodMatcher("org.slf4j.Logger debug(..)"),
            new MethodMatcher("org.slf4j.Logger info(..)"),
            new MethodMatcher("org.slf4j.Logger warn(..)"),
            new MethodMatcher("org.slf4j.Logger error(..)"));

    private static final MethodMatcher THROWABLE_GET_MESSAGE =
            new MethodMatcher("java.lang.Throwable getMessage()", true);

    @Override
    public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final ExecutionContext ctx) {
        final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
        if (!isSingleArgSlf4jLogCall(visited)) {
            return visited;
        }
        return peelThrowableFromConcat(visited).orElse(visited);
    }

    static boolean isSingleArgSlf4jLogCall(final J.MethodInvocation method) {
        if (method.getArguments().size() != 1) {
            return false;
        }
        return SLF4J_LOG_MATCHERS.stream().anyMatch(matcher -> matcher.matches(method));
    }

    static Optional<J.MethodInvocation> peelThrowableFromConcat(final J.MethodInvocation method) {
        final Expression arg = method.getArguments().get(0);
        if (!(arg instanceof J.Binary binary) || binary.getOperator() != J.Binary.Type.Addition) {
            return Optional.empty();
        }
        if (!(binary.getRight() instanceof J.MethodInvocation getMessageCall)
                || !THROWABLE_GET_MESSAGE.matches(getMessageCall)) {
            return Optional.empty();
        }
        final Expression throwable = getMessageCall.getSelect();
        if (throwable == null) {
            return Optional.empty();
        }
        final Expression newMessage = binary.getLeft().withPrefix(arg.getPrefix());
        final Expression throwableArg = throwable.withPrefix(Space.format(" "));
        final JContainer<Expression> newArgs = JContainer.withElements(
                method.getPadding().getArguments(), List.of(newMessage, throwableArg));
        return Optional.of(method.getPadding().withArguments(newArgs));
    }
}