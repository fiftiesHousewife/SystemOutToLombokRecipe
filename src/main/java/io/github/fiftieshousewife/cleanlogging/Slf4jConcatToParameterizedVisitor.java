package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.github.fiftieshousewife.cleanlogging.LogCallTemplate.escape;
import static io.github.fiftieshousewife.cleanlogging.StringConcatDecomposer.flatten;
import static io.github.fiftieshousewife.cleanlogging.StringConcatDecomposer.formatString;
import static io.github.fiftieshousewife.cleanlogging.StringConcatDecomposer.nonLiterals;

@NullMarked
class Slf4jConcatToParameterizedVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final Set<String> SLF4J_LEVELS = Set.of("trace", "debug", "info", "warn", "error");
    private static final String LOG_RECEIVER = "log";

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
        final List<Expression> parts = flatten(binary);
        final List<Expression> substitutions = nonLiterals(parts);
        if (substitutions.isEmpty()) {
            return visited;
        }
        return rebuildAsParameterized(visited, parts, substitutions);
    }

    static boolean isSlf4jLogCall(final J.MethodInvocation method) {
        return SLF4J_LEVELS.contains(method.getSimpleName())
                && method.getSelect() instanceof J.Identifier id
                && LOG_RECEIVER.equals(id.getSimpleName());
    }

    private J.MethodInvocation rebuildAsParameterized(final J.MethodInvocation original,
                                                      final List<Expression> parts,
                                                      final List<Expression> substitutions) {
        final Expression originalFirstArg = original.getArguments().get(0);
        final J.Literal templateLiteral = literalString(formatString(parts), originalFirstArg);
        final List<Expression> finalArgs = new ArrayList<>(substitutions.size() + 1);
        finalArgs.add(templateLiteral);
        finalArgs.addAll(substitutions);
        return original.getPadding().withArguments(
                JContainer.withElements(original.getPadding().getArguments(), finalArgs));
    }

    private static J.Literal literalString(final String value, final Expression original) {
        return new J.Literal(
                Tree.randomId(),
                original.getPrefix(),
                original.getMarkers(),
                value,
                "\"" + escape(value) + "\"",
                null,
                JavaType.Primitive.String);
    }
}
