package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;

import static io.github.fiftieshousewife.cleanlogging.LogCallTemplate.escape;
import static io.github.fiftieshousewife.cleanlogging.PrintfToSlf4jFormatConverter.convert;
import static io.github.fiftieshousewife.cleanlogging.Slf4jConcatToParameterizedVisitor.isSlf4jLogCallWithSingleArg;

@NullMarked
class StringFormatInLogCallVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final MethodMatcher STRING_FORMAT =
            new MethodMatcher("java.lang.String format(java.lang.String, ..)");

    @Override
    public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final ExecutionContext ctx) {
        final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
        if (!isSlf4jLogCallWithSingleArg(visited)) {
            return visited;
        }
        final Expression arg = visited.getArguments().get(0);
        if (!isStringFormatCall(arg)) {
            return visited;
        }
        final J.MethodInvocation formatCall = (J.MethodInvocation) arg;
        final List<Expression> formatArgs = formatCall.getArguments();
        if (formatArgs.isEmpty() || !(formatArgs.get(0) instanceof J.Literal formatLiteral)
                || !(formatLiteral.getValue() instanceof String printfFormat)) {
            return visited;
        }
        return rewriteAsParameterized(visited, printfFormat, formatLiteral, formatArgs);
    }

    private static boolean isStringFormatCall(final Expression expr) {
        return expr instanceof J.MethodInvocation call && STRING_FORMAT.matches(call);
    }

    private J.MethodInvocation rewriteAsParameterized(final J.MethodInvocation original,
                                                      final String printfFormat,
                                                      final J.Literal originalFormatLiteral,
                                                      final List<Expression> originalFormatArgs) {
        final String slf4jFormat = convert(printfFormat);
        final Expression originalFirstArg = original.getArguments().get(0);
        final J.Literal newLiteral = literalStringWithPrefixOf(slf4jFormat, originalFirstArg, originalFormatLiteral);
        final List<Expression> finalArgs = new ArrayList<>(originalFormatArgs.size());
        finalArgs.add(newLiteral);
        finalArgs.addAll(originalFormatArgs.subList(1, originalFormatArgs.size()));
        return original.getPadding().withArguments(
                JContainer.withElements(original.getPadding().getArguments(), finalArgs));
    }

    private static J.Literal literalStringWithPrefixOf(final String value,
                                                       final Expression prefixSource,
                                                       final J.Literal markersSource) {
        return new J.Literal(
                Tree.randomId(),
                prefixSource.getPrefix(),
                markersSource.getMarkers(),
                value,
                "\"" + escape(value) + "\"",
                null,
                JavaType.Primitive.String);
    }
}
