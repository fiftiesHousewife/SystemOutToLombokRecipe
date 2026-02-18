package com.yourorg.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts System.out.println() and System.out.printf() calls to Lombok log statements.
 * Assumes that the class has been annotated with @Log4j2 by AddLombokLog4j2Annotation recipe.
 */
@NullMarked
public class SystemOutToLombokLog4j extends Recipe {

    private static final MethodMatcher SYSTEM_OUT_PRINTLN = new MethodMatcher("java.io.PrintStream println(..)");
    private static final MethodMatcher SYSTEM_OUT_PRINT = new MethodMatcher("java.io.PrintStream print(..)");
    private static final MethodMatcher SYSTEM_OUT_PRINTF = new MethodMatcher("java.io.PrintStream printf(..)");

    @Override
    public String getDisplayName() {
        return "Replace System.out with Lombok log statements";
    }

    @Override
    public String getDescription() {
        return "Replaces System.out.println(), System.out.print(), and System.out.printf() calls " +
                "with appropriate log.info() statements using parameterized logging. " +
                "Also converts System.err calls to log.error().";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                if (!isSystemOutOrErr(mi)) {
                    return mi;
                }

                boolean isSystemErr = isSystemErr(mi);
                String methodName = mi.getSimpleName();

                if (methodName.equals("println") && SYSTEM_OUT_PRINTLN.matches(mi)) {
                    return replacePrintln(mi, isSystemErr);
                }

                if (methodName.equals("print") && SYSTEM_OUT_PRINT.matches(mi)) {
                    return replacePrint(mi, isSystemErr);
                }

                if (methodName.equals("printf") && SYSTEM_OUT_PRINTF.matches(mi)) {
                    return replacePrintf(mi, isSystemErr);
                }

                return mi;
            }

            private boolean isSystemOutOrErr(J.MethodInvocation method) {
                if (method.getSelect() == null) {
                    return false;
                }
                String selectStr = method.getSelect().toString();
                return selectStr.equals("System.out") || selectStr.equals("System.err");
            }

            private boolean isSystemErr(J.MethodInvocation method) {
                return method.getSelect() != null && method.getSelect().toString().equals("System.err");
            }

            private J.MethodInvocation replacePrintln(J.MethodInvocation method, boolean isError) {
                List<Expression> args = method.getArguments();

                if (args.isEmpty()) {
                    return createEmptyLogStatement(method, isError);
                }

                if (args.size() == 1) {
                    return handleSingleArgument(method, args.get(0), isError);
                }

                return method;
            }

            private J.MethodInvocation replacePrint(J.MethodInvocation method, boolean isError) {
                List<Expression> args = method.getArguments();

                if (args.size() == 1) {
                    return handleSingleArgument(method, args.get(0), isError);
                }

                return method;
            }

            private J.MethodInvocation replacePrintf(J.MethodInvocation method, boolean isError) {
                List<Expression> args = method.getArguments();

                if (args.isEmpty()) {
                    return method;
                }

                String template = buildLogCallTemplate(args.size(), isError);
                return JavaTemplate.builder(template)
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), args.toArray());
            }

            private String getLogLevel(boolean isError) {
                return isError ? "error" : "info";
            }

            private J.MethodInvocation createEmptyLogStatement(J.MethodInvocation method, boolean isError) {
                return JavaTemplate.builder("log." + getLogLevel(isError) + "(\"\")")
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace());
            }

            private String buildLogCallTemplate(int argCount, boolean isError) {
                StringBuilder template = new StringBuilder("log.")
                        .append(getLogLevel(isError))
                        .append("(#{any()}");

                for (int i = 1; i < argCount; i++) {
                    template.append(", #{any()}");
                }
                template.append(")");

                return template.toString();
            }

            private J.MethodInvocation handleSingleArgument(J.MethodInvocation method, Expression arg, boolean isError) {
                if (isStringConcatenation(arg)) {
                    return convertToParameterizedLogging(method, (J.Binary) arg, isError);
                }

                return createSimpleLogStatement(method, arg, isError);
            }

            private boolean isStringConcatenation(Expression expression) {
                return expression instanceof J.Binary
                        && ((J.Binary) expression).getOperator() == J.Binary.Type.Addition;
            }

            private J.MethodInvocation createSimpleLogStatement(J.MethodInvocation method, Expression arg, boolean isError) {
                return JavaTemplate.builder("log." + getLogLevel(isError) + "(#{any()})")
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), arg);
            }

            private J.MethodInvocation convertToParameterizedLogging(
                    J.MethodInvocation method,
                    J.Binary binary,
                    boolean isError) {

                List<Expression> parts = new ArrayList<>();
                extractConcatenationParts(binary, parts);

                String formatString = buildFormatString(parts);
                List<Expression> logArgs = extractNonLiteralArguments(parts);

                String template = buildParameterizedLogTemplate(formatString, logArgs.size(), isError);
                return JavaTemplate.builder(template)
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), logArgs.toArray());
            }

            private String buildFormatString(List<Expression> parts) {
                StringBuilder formatString = new StringBuilder();
                for (Expression part : parts) {
                    if (part instanceof J.Literal) {
                        J.Literal literal = (J.Literal) part;
                        if (literal.getValue() != null) {
                            formatString.append(literal.getValue());
                        }
                    } else {
                        formatString.append("{}");
                    }
                }
                return formatString.toString();
            }

            private List<Expression> extractNonLiteralArguments(List<Expression> parts) {
                List<Expression> logArgs = new ArrayList<>();
                for (Expression part : parts) {
                    if (!(part instanceof J.Literal)) {
                        logArgs.add(part);
                    }
                }
                return logArgs;
            }

            private String buildParameterizedLogTemplate(String formatString, int argCount, boolean isError) {
                StringBuilder template = new StringBuilder("log.")
                        .append(getLogLevel(isError))
                        .append("(\"")
                        .append(escapeFormatString(formatString))
                        .append("\"");

                for (int i = 0; i < argCount; i++) {
                    template.append(", #{any()}");
                }
                template.append(")");

                return template.toString();
            }

            private String escapeFormatString(String formatString) {
                return formatString.replace("\\", "\\\\").replace("\"", "\\\"");
            }

            private void extractConcatenationParts(J.Binary binary, List<Expression> parts) {
                if (binary.getLeft() instanceof J.Binary) {
                    J.Binary leftBinary = (J.Binary) binary.getLeft();
                    if (leftBinary.getOperator() == J.Binary.Type.Addition) {
                        extractConcatenationParts(leftBinary, parts);
                    } else {
                        parts.add(binary.getLeft());
                    }
                } else {
                    parts.add(binary.getLeft());
                }

                if (binary.getRight() instanceof J.Binary) {
                    J.Binary rightBinary = (J.Binary) binary.getRight();
                    if (rightBinary.getOperator() == J.Binary.Type.Addition) {
                        extractConcatenationParts(rightBinary, parts);
                    } else {
                        parts.add(binary.getRight());
                    }
                } else {
                    parts.add(binary.getRight());
                }
            }
        };
    }
}
