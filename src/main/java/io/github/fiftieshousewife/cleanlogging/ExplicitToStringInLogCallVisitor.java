package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;

import java.util.ArrayList;
import java.util.List;

import static io.github.fiftieshousewife.cleanlogging.Slf4jConcatToParameterizedVisitor.isSlf4jLogCall;

@NullMarked
class ExplicitToStringInLogCallVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final String TO_STRING = "toString";

    @Override
    public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final ExecutionContext ctx) {
        final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
        if (!isSlf4jLogCall(visited) || visited.getArguments().size() < 2) {
            return visited;
        }
        final List<Expression> args = visited.getArguments();
        final List<Expression> rewritten = new ArrayList<>(args.size());
        rewritten.add(args.get(0));
        boolean changed = false;
        for (int i = 1; i < args.size(); i++) {
            final Expression arg = args.get(i);
            final Expression unwrapped = stripExplicitToString(arg);
            if (unwrapped != arg) {
                changed = true;
            }
            rewritten.add(unwrapped);
        }
        if (!changed) {
            return visited;
        }
        return visited.getPadding().withArguments(
                JContainer.withElements(visited.getPadding().getArguments(), rewritten));
    }

    static Expression stripExplicitToString(final Expression arg) {
        if (!(arg instanceof J.MethodInvocation call)) {
            return arg;
        }
        if (!TO_STRING.equals(call.getSimpleName()) || !isNoArg(call)) {
            return arg;
        }
        final Expression select = call.getSelect();
        if (select == null) {
            return arg;
        }
        return select.withPrefix(arg.getPrefix());
    }

    private static boolean isNoArg(final J.MethodInvocation call) {
        final List<Expression> args = call.getArguments();
        return args.isEmpty() || (args.size() == 1 && args.get(0) instanceof J.Empty);
    }
}
