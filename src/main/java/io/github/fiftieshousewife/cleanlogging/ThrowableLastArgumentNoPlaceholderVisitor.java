package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static io.github.fiftieshousewife.cleanlogging.LogCallTemplate.escape;
import static io.github.fiftieshousewife.cleanlogging.Slf4jConcatToParameterizedVisitor.isSlf4jLogCall;

@NullMarked
class ThrowableLastArgumentNoPlaceholderVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final String THROWABLE_FQN = "java.lang.Throwable";
    private static final String PLACEHOLDER = "{}";

    @Override
    public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final ExecutionContext ctx) {
        final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
        if (!isSlf4jLogCall(visited) || visited.getArguments().size() < 2) {
            return visited;
        }
        final List<Expression> args = visited.getArguments();
        final int lastArgIndex = args.size() - 1;
        if (!isStringLiteral(args.get(0)) || !isThrowable(args.get(lastArgIndex))) {
            return visited;
        }
        final String message = (String) ((J.Literal) args.get(0)).getValue();
        final int placeholderCount = countPlaceholders(message);
        final int substitutionArgs = lastArgIndex;
        if (placeholderCount != substitutionArgs) {
            return visited;
        }
        return rewriteWithTrimmedMessage(visited, message);
    }

    static boolean isStringLiteral(final Expression expr) {
        return expr instanceof J.Literal literal && literal.getValue() instanceof String;
    }

    static boolean isThrowable(final Expression expr) {
        return TypeUtils.isAssignableTo(THROWABLE_FQN, expr.getType());
    }

    static int countPlaceholders(final String message) {
        int count = 0;
        int idx = 0;
        while ((idx = nextPlaceholderIndex(message, idx)) >= 0) {
            count++;
            idx += PLACEHOLDER.length();
        }
        return count;
    }

    static int nextPlaceholderIndex(final String message, final int from) {
        int idx = message.indexOf(PLACEHOLDER, from);
        while (idx > 0 && message.charAt(idx - 1) == '\\') {
            idx = message.indexOf(PLACEHOLDER, idx + PLACEHOLDER.length());
        }
        return idx;
    }

    static String dropLastPlaceholder(final String message) {
        int last = -1;
        int idx = 0;
        while ((idx = nextPlaceholderIndex(message, idx)) >= 0) {
            last = idx;
            idx += PLACEHOLDER.length();
        }
        if (last < 0) {
            return message;
        }
        final String before = message.substring(0, last);
        final String after = message.substring(last + PLACEHOLDER.length());
        return trimTrailingSeparator(before) + after;
    }

    private static String trimTrailingSeparator(final String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == ':' || s.charAt(end - 1) == ',')) {
            end--;
        }
        return s.substring(0, end);
    }

    private J.MethodInvocation rewriteWithTrimmedMessage(final J.MethodInvocation original, final String originalMessage) {
        final String trimmed = dropLastPlaceholder(originalMessage);
        if (trimmed.isEmpty()) {
            return original;
        }
        final J.Literal originalLiteral = (J.Literal) original.getArguments().get(0);
        final J.Literal newLiteral = originalLiteral
                .withValue(trimmed)
                .withValueSource("\"" + escape(trimmed) + "\"");
        final List<Expression> newArgs = new ArrayList<>(original.getArguments());
        newArgs.set(0, newLiteral);
        return original.getPadding().withArguments(
                JContainer.withElements(original.getPadding().getArguments(), newArgs));
    }
}
