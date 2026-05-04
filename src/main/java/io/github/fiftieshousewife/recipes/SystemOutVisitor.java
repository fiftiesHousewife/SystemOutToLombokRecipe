package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

import static io.github.fiftieshousewife.recipes.LogCallTemplate.argsOnly;
import static io.github.fiftieshousewife.recipes.LogCallTemplate.emptyMessage;
import static io.github.fiftieshousewife.recipes.LogCallTemplate.parameterized;
import static io.github.fiftieshousewife.recipes.LombokClasspathGate.isAvailable;
import static io.github.fiftieshousewife.recipes.PrintfToSlf4jFormatConverter.convert;
import static io.github.fiftieshousewife.recipes.StringConcatDecomposer.flatten;
import static io.github.fiftieshousewife.recipes.StringConcatDecomposer.formatString;
import static io.github.fiftieshousewife.recipes.StringConcatDecomposer.nonLiterals;
import static io.github.fiftieshousewife.recipes.SystemOutToSlf4j.hasNoRealArg;
import static io.github.fiftieshousewife.recipes.SystemOutToSlf4j.isSystemErr;
import static io.github.fiftieshousewife.recipes.SystemOutToSlf4j.isSystemOutOrErr;

@NullMarked
class SystemOutVisitor extends JavaIsoVisitor<ExecutionContext> {

    private final boolean requireLombokOnClasspath;

    SystemOutVisitor(final boolean requireLombokOnClasspath) {
        this.requireLombokOnClasspath = requireLombokOnClasspath;
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final ExecutionContext ctx) {
        final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
        return shouldRewrite(visited) ? dispatchByMethodName(visited) : visited;
    }

    private boolean shouldRewrite(final J.MethodInvocation method) {
        return isSystemOutOrErr(method)
                && (!requireLombokOnClasspath || isAvailable(getCursor()));
    }

    private J.MethodInvocation dispatchByMethodName(final J.MethodInvocation visited) {
        final boolean isError = isSystemErr(visited);
        return PrintMethod.forCall(visited)
                .map(printMethod -> printMethod.apply(this, visited, isError))
                .orElse(visited);
    }

    J.MethodInvocation replacePrintln(final J.MethodInvocation method, final boolean isError) {
        return hasNoRealArg(method.getArguments())
                ? applyTemplate(method, emptyMessage(isError))
                : replacePrint(method, isError);
    }

    J.MethodInvocation replacePrint(final J.MethodInvocation method, final boolean isError) {
        final List<Expression> args = method.getArguments();
        if (args.size() == 1) {
            return handleSingleArgument(method, args.get(0), isError);
        }
        return method;
    }

    J.MethodInvocation replacePrintf(final J.MethodInvocation method, final boolean isError) {
        final List<Expression> args = method.getArguments();
        if (hasNoRealArg(args)) {
            return method;
        }
        if (args.get(0) instanceof J.Literal literal && literal.getValue() instanceof String printfFormat) {
            final String log4jFormat = convert(printfFormat);
            final List<Expression> rest = args.subList(1, args.size());
            return applyTemplate(method,
                    parameterized(log4jFormat, rest.size(), isError),
                    rest.toArray());
        }
        return applyTemplate(method,
                argsOnly(args.size(), isError),
                args.toArray());
    }

    J.MethodInvocation handleSingleArgument(final J.MethodInvocation method, final Expression arg, final boolean isError) {
        if (arg instanceof J.Binary binary && binary.getOperator() == J.Binary.Type.Addition) {
            final List<Expression> parts = flatten(binary);
            final String format = formatString(parts);
            final List<Expression> logArgs = nonLiterals(parts);
            return applyTemplate(method,
                    parameterized(format, logArgs.size(), isError),
                    logArgs.toArray());
        }
        return applyTemplate(method, argsOnly(1, isError), arg);
    }

    J.MethodInvocation applyTemplate(final J.MethodInvocation method, final String template, final Object... args) {
        return JavaTemplate.builder(template)
                .build()
                .apply(getCursor(), method.getCoordinates().replace(), args);
    }
}
