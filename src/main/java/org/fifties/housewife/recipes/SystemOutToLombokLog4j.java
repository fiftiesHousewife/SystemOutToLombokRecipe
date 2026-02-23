package org.fifties.housewife.recipes;

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

            boolean isSystemOutOrErr(J.MethodInvocation method) {
                if (method.getSelect() == null) {
                    return false;
                }
                String selectStr = method.getSelect().toString();
                return selectStr.equals("System.out") || selectStr.equals("System.err");
            }

            boolean isSystemErr(J.MethodInvocation method) {
                return method.getSelect() != null && method.getSelect().toString().equals("System.err");
            }

            J.MethodInvocation replacePrintln(J.MethodInvocation method, boolean isError) {
                List<Expression> args = method.getArguments();

                if (args.isEmpty() || (args.size() == 1 && args.get(0) instanceof J.Empty)) {
                    return createEmptyLogStatement(method, isError);
                }

                if (args.size() == 1) {
                    return handleSingleArgument(method, args.get(0), isError);
                }

                return method;
            }

            J.MethodInvocation replacePrint(J.MethodInvocation method, boolean isError) {
                List<Expression> args = method.getArguments();

                if (args.size() == 1) {
                    return handleSingleArgument(method, args.get(0), isError);
                }

                return method;
            }

            J.MethodInvocation replacePrintf(J.MethodInvocation method, boolean isError) {
                List<Expression> args = method.getArguments();

                if (args.isEmpty() || (args.size() == 1 && args.get(0) instanceof J.Empty)) {
                    return method;
                }

                if (args.get(0) instanceof J.Literal) {
                    J.Literal formatLiteral = (J.Literal) args.get(0);
                    if (formatLiteral.getValue() instanceof String) {
                        String log4jFormat = convertPrintfToLog4jFormat((String) formatLiteral.getValue());
                        List<Expression> remainingArgs = args.subList(1, args.size());
                        String template = buildParameterizedLogTemplate(log4jFormat, remainingArgs.size(), isError);
                        return JavaTemplate.builder(template)
                                .build()
                                .apply(getCursor(), method.getCoordinates().replace(), remainingArgs.toArray());
                    }
                }

                String template = buildLogCallTemplate(args.size(), isError);
                return JavaTemplate.builder(template)
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), args.toArray());
            }

            String convertPrintfToLog4jFormat(String printfFormat) {
                StringBuilder result = new StringBuilder();
                int i = 0;
                while (i < printfFormat.length()) {
                    if (printfFormat.charAt(i) != '%') {
                        result.append(printfFormat.charAt(i++));
                        continue;
                    }
                    i++;
                    if (i >= printfFormat.length()) {
                        result.append('%');
                        break;
                    }
                    char specifier = printfFormat.charAt(i);
                    if (specifier == 'n') {
                        i++;
                    } else if (specifier == '%') {
                        result.append('%');
                        i++;
                    } else {
                        i = skipSpecifier(printfFormat, i);
                        result.append("{}");
                    }
                }
                return result.toString();
            }

            int skipSpecifier(String format, int i) {
                i = skipArgumentIndex(format, i);
                i = skipFlags(format, i);
                i = skipWidth(format, i);
                i = skipPrecision(format, i);
                return skipConversionChar(format, i);
            }

            int skipArgumentIndex(String format, int i) {
                int mark = i;
                while (i < format.length() && Character.isDigit(format.charAt(i))) {
                    i++;
                }
                if (i < format.length() && format.charAt(i) == '$') {
                    return i + 1;
                }
                return mark;
            }

            int skipFlags(String format, int i) {
                while (i < format.length() && "+-0 #,(".indexOf(format.charAt(i)) >= 0) {
                    i++;
                }
                return i;
            }

            int skipWidth(String format, int i) {
                while (i < format.length() && Character.isDigit(format.charAt(i))) {
                    i++;
                }
                return i;
            }

            int skipPrecision(String format, int i) {
                if (i >= format.length() || format.charAt(i) != '.') {
                    return i;
                }
                i++;
                while (i < format.length() && Character.isDigit(format.charAt(i))) {
                    i++;
                }
                return i;
            }

            int skipConversionChar(String format, int i) {
                if (i >= format.length()) {
                    return i;
                }
                char conv = format.charAt(i++);
                if (isDateTimeConversion(conv) && i < format.length()) {
                    i++;
                }
                return i;
            }

            boolean isDateTimeConversion(char conv) {
                return conv == 't' || conv == 'T';
            }

            String getLogLevel(boolean isError) {
                return isError ? "error" : "info";
            }

            J.MethodInvocation createEmptyLogStatement(J.MethodInvocation method, boolean isError) {
                return JavaTemplate.builder("log." + getLogLevel(isError) + "(\"\")")
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace());
            }

            String buildLogCallTemplate(int argCount, boolean isError) {
                StringBuilder template = new StringBuilder("log.")
                        .append(getLogLevel(isError))
                        .append("(#{any()}");

                for (int i = 1; i < argCount; i++) {
                    template.append(", #{any()}");
                }
                template.append(")");

                return template.toString();
            }

            J.MethodInvocation handleSingleArgument(J.MethodInvocation method, Expression arg, boolean isError) {
                if (isStringConcatenation(arg)) {
                    return convertToParameterizedLogging(method, (J.Binary) arg, isError);
                }

                return createSimpleLogStatement(method, arg, isError);
            }

            boolean isStringConcatenation(Expression expression) {
                return expression instanceof J.Binary
                        && ((J.Binary) expression).getOperator() == J.Binary.Type.Addition;
            }

            J.MethodInvocation createSimpleLogStatement(J.MethodInvocation method, Expression arg, boolean isError) {
                return JavaTemplate.builder("log." + getLogLevel(isError) + "(#{any()})")
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), arg);
            }

            J.MethodInvocation convertToParameterizedLogging(
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

            String buildFormatString(List<Expression> parts) {
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

            List<Expression> extractNonLiteralArguments(List<Expression> parts) {
                List<Expression> logArgs = new ArrayList<>();
                for (Expression part : parts) {
                    if (!(part instanceof J.Literal)) {
                        logArgs.add(part);
                    }
                }
                return logArgs;
            }

            String buildParameterizedLogTemplate(String formatString, int argCount, boolean isError) {
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

            String escapeFormatString(String formatString) {
                return formatString.replace("\\", "\\\\").replace("\"", "\\\"");
            }

            void extractConcatenationParts(J.Binary binary, List<Expression> parts) {
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
