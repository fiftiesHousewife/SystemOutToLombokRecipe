package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;

/**
 * Builds the string templates that OpenRewrite's {@code JavaTemplate} will
 * parse into log-call AST nodes. Separated from the visitor so the string
 * shape is unit-testable without ever constructing a J.MethodInvocation.
 */
@NullMarked
final class LogCallTemplate {

    private LogCallTemplate() {
    }

    static String logLevel(boolean isError) {
        return isError ? "error" : "info";
    }

    static String argsOnly(int argCount, boolean isError) {
        return "log." + logLevel(isError) + "(#{any()}" + ", #{any()}".repeat(argCount - 1) + ")";
    }

    static String parameterized(String formatString, int argCount, boolean isError) {
        return "log." + logLevel(isError)
                + "(\"" + escape(formatString) + "\""
                + ", #{any()}".repeat(argCount)
                + ")";
    }

    static String escape(String formatString) {
        return formatString.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
