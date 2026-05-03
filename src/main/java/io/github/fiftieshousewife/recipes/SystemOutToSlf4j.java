package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.Objects;

import static io.github.fiftieshousewife.recipes.LogCallTemplate.argsOnly;
import static io.github.fiftieshousewife.recipes.LogCallTemplate.emptyMessage;
import static io.github.fiftieshousewife.recipes.LogCallTemplate.parameterized;
import static io.github.fiftieshousewife.recipes.LombokClasspathGate.isAvailable;
import static io.github.fiftieshousewife.recipes.PrintfToSlf4jFormatConverter.convert;
import static io.github.fiftieshousewife.recipes.StringConcatDecomposer.flatten;
import static io.github.fiftieshousewife.recipes.StringConcatDecomposer.formatString;
import static io.github.fiftieshousewife.recipes.StringConcatDecomposer.nonLiterals;

/**
 * Converts {@code System.out} and {@code System.err} print calls to Lombok
 * {@code log.xxx(...)} statements. Assumes the class has already been annotated
 * with {@code @Slf4j} (apply {@link AddLombokSlf4jAnnotation} first).
 *
 * <p>When {@code requireLombokOnClasspath} is set, the rewrite is skipped in
 * source files whose classpath doesn't contain {@code lombok.extern.slf4j.Slf4j}
 * — without {@code @Slf4j} there is no {@code log} field, so rewriting the
 * {@code System.out} call would produce uncompilable Java.
 */
@NullMarked
public class SystemOutToSlf4j extends Recipe {

    private static final String SYSTEM_OUT = "System.out";
    private static final String SYSTEM_ERR = "System.err";

    @Option(displayName = "Require Lombok on classpath",
            description = "When true, only rewrite System.out calls in source files where " +
                    "lombok.extern.slf4j.Slf4j is resolvable on the classpath.",
            required = false)
    private final boolean requireLombokOnClasspath;

    public SystemOutToSlf4j() {
        this(false);
    }

    public SystemOutToSlf4j(final boolean requireLombokOnClasspath) {
        this.requireLombokOnClasspath = requireLombokOnClasspath;
    }

    @SuppressWarnings("unused")
    public boolean isRequireLombokOnClasspath() {
        return requireLombokOnClasspath;
    }

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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof SystemOutToSlf4j other)) return false;
        return requireLombokOnClasspath == other.requireLombokOnClasspath;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requireLombokOnClasspath);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>("java.io.PrintStream println(..)"),
                        new UsesMethod<>("java.io.PrintStream print(..)"),
                        new UsesMethod<>("java.io.PrintStream printf(..)")),
                new SystemOutVisitor(requireLombokOnClasspath));
    }

    static class SystemOutVisitor extends JavaIsoVisitor<ExecutionContext> {

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
            final List<Expression> args = method.getArguments();
            if (hasNoRealArg(args)) {
                return applyTemplate(method, emptyMessage(isError));
            }
            if (args.size() == 1) {
                return handleSingleArgument(method, args.get(0), isError);
            }
            return method;
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

    static boolean isSystemOutOrErr(final J.MethodInvocation method) {
        final Expression select = method.getSelect();
        return select!=null && (SYSTEM_OUT.equals(select.toString()) || SYSTEM_ERR.equals(select.toString()));
    }

    static boolean isSystemErr(final J.MethodInvocation method) {
        final Expression select = method.getSelect();
        return select != null && SYSTEM_ERR.equals(select.toString());
    }

    static boolean hasNoRealArg(final List<Expression> args) {
        return args.isEmpty() || (args.size() == 1 && args.get(0) instanceof J.Empty);
    }
}
