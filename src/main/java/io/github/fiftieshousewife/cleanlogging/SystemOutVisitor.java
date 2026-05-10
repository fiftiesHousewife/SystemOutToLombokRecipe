package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

import static io.github.fiftieshousewife.cleanlogging.LogCallTemplate.argsOnly;
import static io.github.fiftieshousewife.cleanlogging.LogCallTemplate.emptyMessage;
import static io.github.fiftieshousewife.cleanlogging.LogCallTemplate.parameterized;
import static io.github.fiftieshousewife.cleanlogging.LombokClasspathGate.isAvailable;
import static io.github.fiftieshousewife.cleanlogging.PrintfToSlf4jFormatConverter.convert;
import static io.github.fiftieshousewife.cleanlogging.StringConcatDecomposer.flatten;
import static io.github.fiftieshousewife.cleanlogging.StringConcatDecomposer.formatString;
import static io.github.fiftieshousewife.cleanlogging.StringConcatDecomposer.nonLiterals;
import static io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4j.hasNoRealArg;
import static io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4j.isSystemErr;
import static io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4j.isSystemOutOrErr;

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
                ? applyTemplate(getCursor(), method, emptyMessage(isError))
                : replacePrint(method, isError);
    }

    J.MethodInvocation replacePrint(final J.MethodInvocation method, final boolean isError) {
        final List<Expression> args = method.getArguments();
        if (args.size() == 1) {
            return handleSingleArgument(getCursor(), method, args.get(0), isError);
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
            return applyTemplate(getCursor(), method,
                    parameterized(log4jFormat, rest.size(), isError),
                    rest.toArray());
        }
        return applyTemplate(getCursor(), method,
                argsOnly(args.size(), isError),
                args.toArray());
    }

    static J.MethodInvocation handleSingleArgument(final Cursor cursor, final J.MethodInvocation method,
                                                   final Expression arg, final boolean isError) {
        if (arg instanceof J.Binary binary && binary.getOperator() == J.Binary.Type.Addition) {
            final List<Expression> parts = flatten(binary);
            final String format = formatString(parts);
            final List<Expression> logArgs = nonLiterals(parts);
            return applyTemplate(cursor, method,
                    parameterized(format, logArgs.size(), isError),
                    logArgs.toArray());
        }
        return applyTemplate(cursor, method, argsOnly(1, isError), arg);
    }

    static J.MethodInvocation applyTemplate(final Cursor cursor, final J.MethodInvocation method,
                                            final String template, final Object... args) {
        return JavaTemplate.builder(template)
                .build()
                .apply(cursor, method.getCoordinates().replace(), args);
    }
}
