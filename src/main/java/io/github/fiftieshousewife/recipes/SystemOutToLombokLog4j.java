package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

/**
 * Converts {@code System.out} and {@code System.err} print calls to Lombok
 * {@code log.xxx(...)} statements. Assumes the class has already been annotated
 * with {@code @Log4j2} (apply {@link AddLombokLog4j2Annotation} first).
 */
@NullMarked
public class SystemOutToLombokLog4j extends Recipe {

    private static final String SYSTEM_OUT = "System.out";
    private static final String SYSTEM_ERR = "System.err";

    private static final MethodMatcher PRINTLN = new MethodMatcher("java.io.PrintStream println(..)");
    private static final MethodMatcher PRINT = new MethodMatcher("java.io.PrintStream print(..)");
    private static final MethodMatcher PRINTF = new MethodMatcher("java.io.PrintStream printf(..)");

    @Override
    public String getDisplayName() {
        return "Replace System.out with Lombok log statements";
    }

    @Override
    public String getDescription() {
        return "Replaces System.out.println(), System.out.print(), and System.out.printf() calls "
                + "with appropriate log.info() statements using parameterized logging. "
                + "Also converts System.err calls to log.error().";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SystemOutVisitor();
    }

    static class SystemOutVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final ExecutionContext ctx) {
            final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
            if (!isSystemOutOrErr(visited)) {
                return visited;
            }
            final boolean isError = isSystemErr(visited);
            final String name = visited.getSimpleName();
            if ("println".equals(name) && PRINTLN.matches(visited)) {
                return replacePrintln(visited, isError);
            }
            if ("print".equals(name) && PRINT.matches(visited)) {
                return replacePrint(visited, isError);
            }
            if ("printf".equals(name) && PRINTF.matches(visited)) {
                return replacePrintf(visited, isError);
            }
            return visited;
        }

        private J.MethodInvocation replacePrintln(final J.MethodInvocation method, final boolean isError) {
            final List<Expression> args = method.getArguments();
            if (hasNoRealArg(args)) {
                return applyTemplate(method, "log." + LogCallTemplate.logLevel(isError) + "(\"\")");
            }
            if (args.size() == 1) {
                return handleSingleArgument(method, args.get(0), isError);
            }
            return method;
        }

        private J.MethodInvocation replacePrint(final J.MethodInvocation method, final boolean isError) {
            final List<Expression> args = method.getArguments();
            if (args.size() == 1) {
                return handleSingleArgument(method, args.get(0), isError);
            }
            return method;
        }

        private J.MethodInvocation replacePrintf(final J.MethodInvocation method, final boolean isError) {
            final List<Expression> args = method.getArguments();
            if (hasNoRealArg(args)) {
                return method;
            }
            if (args.get(0) instanceof J.Literal literal && literal.getValue() instanceof String printfFormat) {
                final String log4jFormat = PrintfToLog4jFormatConverter.convert(printfFormat);
                final List<Expression> rest = args.subList(1, args.size());
                return applyTemplate(method,
                        LogCallTemplate.parameterized(log4jFormat, rest.size(), isError),
                        rest.toArray());
            }
            return applyTemplate(method,
                    LogCallTemplate.argsOnly(args.size(), isError),
                    args.toArray());
        }

        private J.MethodInvocation handleSingleArgument(final J.MethodInvocation method, final Expression arg, final boolean isError) {
            if (arg instanceof J.Binary binary && binary.getOperator() == J.Binary.Type.Addition) {
                final List<Expression> parts = StringConcatDecomposer.flatten(binary);
                final String format = StringConcatDecomposer.formatString(parts);
                final List<Expression> logArgs = StringConcatDecomposer.nonLiterals(parts);
                return applyTemplate(method,
                        LogCallTemplate.parameterized(format, logArgs.size(), isError),
                        logArgs.toArray());
            }
            return applyTemplate(method,
                    "log." + LogCallTemplate.logLevel(isError) + "(#{any()})",
                    arg);
        }

        private J.MethodInvocation applyTemplate(final J.MethodInvocation method, final String template, final Object... args) {
            return JavaTemplate.builder(template)
                    .build()
                    .apply(getCursor(), method.getCoordinates().replace(), args);
        }
    }

    static boolean isSystemOutOrErr(final J.MethodInvocation method) {
        final Expression select = method.getSelect();
        if (select == null) {
            return false;
        }
        final String selectStr = select.toString();
        return SYSTEM_OUT.equals(selectStr) || SYSTEM_ERR.equals(selectStr);
    }

    static boolean isSystemErr(final J.MethodInvocation method) {
        final Expression select = method.getSelect();
        return select != null && SYSTEM_ERR.equals(select.toString());
    }

    static boolean hasNoRealArg(final List<Expression> args) {
        return args.isEmpty() || (args.size() == 1 && args.get(0) instanceof J.Empty);
    }
}
