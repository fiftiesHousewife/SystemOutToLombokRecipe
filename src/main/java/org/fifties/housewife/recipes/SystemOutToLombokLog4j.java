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
 * Converts System.out and System.err print calls to Lombok log statements.
 * Assumes the class has been annotated with {@code @Log4j2} (apply
 * {@link AddLombokLog4j2Annotation} first).
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
        return new SystemOutVisitor();
    }

    static class SystemOutVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            if (!isSystemOutOrErr(mi)) {
                return mi;
            }

            boolean isError = isSystemErr(mi);
            String methodName = mi.getSimpleName();

            if ("println".equals(methodName) && SYSTEM_OUT_PRINTLN.matches(mi)) {
                return replacePrintln(mi, isError);
            }
            if ("print".equals(methodName) && SYSTEM_OUT_PRINT.matches(mi)) {
                return replacePrint(mi, isError);
            }
            if ("printf".equals(methodName) && SYSTEM_OUT_PRINTF.matches(mi)) {
                return replacePrintf(mi, isError);
            }
            return mi;
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
            if (args.get(0) instanceof J.Literal formatLiteral
                    && formatLiteral.getValue() instanceof String printfFormat) {
                String log4jFormat = convertPrintfToLog4jFormat(printfFormat);
                List<Expression> remainingArgs = args.subList(1, args.size());
                String template = buildParameterizedLogTemplate(log4jFormat, remainingArgs.size(), isError);
                return JavaTemplate.builder(template)
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), remainingArgs.toArray());
            }
            String template = buildLogCallTemplate(args.size(), isError);
            return JavaTemplate.builder(template)
                    .build()
                    .apply(getCursor(), method.getCoordinates().replace(), args.toArray());
        }

        J.MethodInvocation createEmptyLogStatement(J.MethodInvocation method, boolean isError) {
            return JavaTemplate.builder("log." + getLogLevel(isError) + "(\"\")")
                    .build()
                    .apply(getCursor(), method.getCoordinates().replace());
        }

        J.MethodInvocation handleSingleArgument(J.MethodInvocation method, Expression arg, boolean isError) {
            if (arg instanceof J.Binary binary && binary.getOperator() == J.Binary.Type.Addition) {
                return convertToParameterizedLogging(method, binary, isError);
            }
            return createSimpleLogStatement(method, arg, isError);
        }

        J.MethodInvocation createSimpleLogStatement(J.MethodInvocation method, Expression arg, boolean isError) {
            return JavaTemplate.builder("log." + getLogLevel(isError) + "(#{any()})")
                    .build()
                    .apply(getCursor(), method.getCoordinates().replace(), arg);
        }

        J.MethodInvocation convertToParameterizedLogging(J.MethodInvocation method, J.Binary binary, boolean isError) {
            List<Expression> parts = new ArrayList<>();
            extractConcatenationParts(binary, parts);

            String formatString = buildFormatString(parts);
            List<Expression> logArgs = extractNonLiteralArguments(parts);
            String template = buildParameterizedLogTemplate(formatString, logArgs.size(), isError);

            return JavaTemplate.builder(template)
                    .build()
                    .apply(getCursor(), method.getCoordinates().replace(), logArgs.toArray());
        }
    }

    static boolean isSystemOutOrErr(J.MethodInvocation method) {
        Expression select = method.getSelect();
        if (select == null) {
            return false;
        }
        String selectStr = select.toString();
        return "System.out".equals(selectStr) || "System.err".equals(selectStr);
    }

    static boolean isSystemErr(J.MethodInvocation method) {
        Expression select = method.getSelect();
        return select != null && "System.err".equals(select.toString());
    }

    static String getLogLevel(boolean isError) {
        return isError ? "error" : "info";
    }

    static String buildLogCallTemplate(int argCount, boolean isError) {
        return "log." + getLogLevel(isError) + "(#{any()}" + ", #{any()}".repeat(argCount - 1) + ")";
    }

    static String buildParameterizedLogTemplate(String formatString, int argCount, boolean isError) {
        return "log." + getLogLevel(isError)
                + "(\"" + escapeFormatString(formatString) + "\""
                + ", #{any()}".repeat(argCount)
                + ")";
    }

    static String escapeFormatString(String formatString) {
        return formatString.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String buildFormatString(List<Expression> parts) {
        StringBuilder formatString = new StringBuilder();
        for (Expression part : parts) {
            if (part instanceof J.Literal literal && literal.getValue() != null) {
                formatString.append(literal.getValue());
            } else {
                formatString.append("{}");
            }
        }
        return formatString.toString();
    }

    static List<Expression> extractNonLiteralArguments(List<Expression> parts) {
        List<Expression> logArgs = new ArrayList<>();
        for (Expression part : parts) {
            if (!(part instanceof J.Literal)) {
                logArgs.add(part);
            }
        }
        return logArgs;
    }

    static void extractConcatenationParts(J.Binary binary, List<Expression> parts) {
        if (binary.getLeft() instanceof J.Binary leftBinary
                && leftBinary.getOperator() == J.Binary.Type.Addition) {
            extractConcatenationParts(leftBinary, parts);
        } else {
            parts.add(binary.getLeft());
        }
        if (binary.getRight() instanceof J.Binary rightBinary
                && rightBinary.getOperator() == J.Binary.Type.Addition) {
            extractConcatenationParts(rightBinary, parts);
        } else {
            parts.add(binary.getRight());
        }
    }

    static String convertPrintfToLog4jFormat(String printfFormat) {
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

    static int skipSpecifier(String format, int i) {
        i = skipArgumentIndex(format, i);
        i = skipFlags(format, i);
        i = skipWidth(format, i);
        i = skipPrecision(format, i);
        return skipConversionChar(format, i);
    }

    static int skipArgumentIndex(String format, int i) {
        int mark = i;
        while (i < format.length() && Character.isDigit(format.charAt(i))) {
            i++;
        }
        if (i < format.length() && format.charAt(i) == '$') {
            return i + 1;
        }
        return mark;
    }

    static int skipFlags(String format, int i) {
        while (i < format.length() && "+-0 #,(".indexOf(format.charAt(i)) >= 0) {
            i++;
        }
        return i;
    }

    static int skipWidth(String format, int i) {
        while (i < format.length() && Character.isDigit(format.charAt(i))) {
            i++;
        }
        return i;
    }

    static int skipPrecision(String format, int i) {
        if (i >= format.length() || format.charAt(i) != '.') {
            return i;
        }
        return skipWidth(format, i + 1);
    }

    static int skipConversionChar(String format, int i) {
        if (i >= format.length()) {
            return i;
        }
        char conv = format.charAt(i++);
        if (isDateTimeConversion(conv) && i < format.length()) {
            i++;
        }
        return i;
    }

    static boolean isDateTimeConversion(char conv) {
        return conv == 't' || conv == 'T';
    }
}
