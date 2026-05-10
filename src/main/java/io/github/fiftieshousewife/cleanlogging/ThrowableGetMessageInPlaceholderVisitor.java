package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static io.github.fiftieshousewife.cleanlogging.Slf4jConcatToParameterizedVisitor.isSlf4jLogCall;
import static io.github.fiftieshousewife.cleanlogging.ThrowableLastArgumentNoPlaceholderVisitor.countPlaceholders;

@NullMarked
class ThrowableGetMessageInPlaceholderVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final String THROWABLE_FQN = "java.lang.Throwable";
    private static final String GET_MESSAGE = "getMessage";

    @Override
    public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final ExecutionContext ctx) {
        final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
        if (!isSlf4jLogCall(visited) || visited.getArguments().size() < 2) {
            return visited;
        }
        final List<Expression> args = visited.getArguments();
        if (!isStringLiteral(args.get(0))) {
            return visited;
        }
        final Expression lastArg = args.get(args.size() - 1);
        final Expression throwableReceiver = throwableReceiverOfGetMessage(lastArg);
        if (throwableReceiver == null) {
            return visited;
        }
        final String message = (String) ((J.Literal) args.get(0)).getValue();
        final int substitutionArgs = args.size() - 1;
        if (countPlaceholders(message) != substitutionArgs) {
            return visited;
        }
        return appendThrowableArg(visited, throwableReceiver);
    }

    private static boolean isStringLiteral(final Expression expr) {
        return expr instanceof J.Literal literal && literal.getValue() instanceof String;
    }

    static @Nullable Expression throwableReceiverOfGetMessage(final Expression expr) {
        if (!(expr instanceof J.MethodInvocation call)) {
            return null;
        }
        if (!GET_MESSAGE.equals(call.getSimpleName()) || !isNoArg(call)) {
            return null;
        }
        final Expression select = call.getSelect();
        if (select == null || !TypeUtils.isAssignableTo(THROWABLE_FQN, select.getType())) {
            return null;
        }
        return select;
    }

    private static boolean isNoArg(final J.MethodInvocation call) {
        final List<Expression> args = call.getArguments();
        return args.isEmpty() || (args.size() == 1 && args.get(0) instanceof J.Empty);
    }

    private J.MethodInvocation appendThrowableArg(final J.MethodInvocation original, final Expression throwable) {
        final List<Expression> newArgs = new ArrayList<>(original.getArguments());
        newArgs.add(throwable.withPrefix(Space.SINGLE_SPACE));
        return original.getPadding().withArguments(
                JContainer.withElements(original.getPadding().getArguments(), newArgs));
    }
}
