package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@NullMarked
class Slf4jConcatToParameterizedVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final Set<String> SLF4J_LEVELS = Set.of("trace", "debug", "info", "warn", "error");
    private static final String LOG_RECEIVER = "log";
    private static final String PLACEHOLDER = "{}";

    @Override
    public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final ExecutionContext ctx) {
        final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
        if (!isSlf4jLogCall(visited) || visited.getArguments().size() != 1) {
            return visited;
        }
        final Expression arg = visited.getArguments().get(0);
        if (!(arg instanceof J.Binary binary) || binary.getOperator() != J.Binary.Type.Addition) {
            return visited;
        }
        final List<Expression> parts = flattenAddition(binary);
        if (!hasNonStringPart(parts)) {
            return visited;
        }
        return rebuildAsParameterized(visited, parts);
    }

    static boolean isSlf4jLogCall(final J.MethodInvocation method) {
        return SLF4J_LEVELS.contains(method.getSimpleName())
                && method.getSelect() instanceof J.Identifier id
                && LOG_RECEIVER.equals(id.getSimpleName());
    }

    static List<Expression> flattenAddition(final Expression expr) {
        final List<Expression> out = new ArrayList<>();
        flattenInto(expr, out);
        return out;
    }

    private static void flattenInto(final Expression expr, final List<Expression> out) {
        if (expr instanceof J.Binary binary && binary.getOperator() == J.Binary.Type.Addition) {
            flattenInto(binary.getLeft(), out);
            flattenInto(binary.getRight(), out);
        } else {
            out.add(expr);
        }
    }

    static boolean hasNonStringPart(final List<Expression> parts) {
        return parts.stream().anyMatch(part -> !isStringLiteral(part));
    }

    private static boolean isStringLiteral(final Expression expr) {
        return expr instanceof J.Literal literal && literal.getValue() instanceof String;
    }

    private J.MethodInvocation rebuildAsParameterized(final J.MethodInvocation original, final List<Expression> parts) {
        final StringBuilder template = new StringBuilder();
        final List<Expression> newArgs = new ArrayList<>();
        for (final Expression part : parts) {
            if (isStringLiteral(part)) {
                template.append((String) ((J.Literal) part).getValue());
            } else {
                template.append(PLACEHOLDER);
                newArgs.add(part);
            }
        }
        final Expression originalFirstArg = original.getArguments().get(0);
        final J.Literal templateLiteral = literalString(template.toString(), originalFirstArg);
        final List<Expression> finalArgs = new ArrayList<>(newArgs.size() + 1);
        finalArgs.add(templateLiteral);
        finalArgs.addAll(newArgs);
        final JContainer<Expression> container = JContainer.withElements(
                original.getPadding().getArguments(), finalArgs);
        return original.getPadding().withArguments(container);
    }

    private static J.Literal literalString(final String value, final Expression original) {
        return new J.Literal(
                org.openrewrite.Tree.randomId(),
                original.getPrefix(),
                original.getMarkers(),
                value,
                "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"",
                null,
                org.openrewrite.java.tree.JavaType.Primitive.String);
    }
}
