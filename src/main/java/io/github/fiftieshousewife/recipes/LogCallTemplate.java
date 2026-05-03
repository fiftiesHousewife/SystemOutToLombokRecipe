package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;

@NullMarked
final class LogCallTemplate {
    private static final String PLACEHOLDER = "#{any()}";
    private static final String LEADING_COMMA_PLACEHOLDER = ", " + PLACEHOLDER;
    private static final String CALL_TEMPLATE = "log.%s(%s)";
    private static final String EMPTY_MESSAGE_TEMPLATE = "log.%s(\"\")";
    private static final String FORMATTED_CALL_TEMPLATE = "log.%s(\"%s\"%s)";

    private LogCallTemplate() {
    }

    static String logLevel(boolean isError) {
        return isError ? "error" : "info";
    }

    static String emptyMessage(boolean isError) {
        return EMPTY_MESSAGE_TEMPLATE.formatted(logLevel(isError));
    }

    static String argsOnly(int argCount, boolean isError) {
        return CALL_TEMPLATE.formatted(logLevel(isError),
                PLACEHOLDER + LEADING_COMMA_PLACEHOLDER.repeat(argCount - 1));
    }

    static String parameterized(String formatString, int argCount, boolean isError) {
        return FORMATTED_CALL_TEMPLATE.formatted(
                logLevel(isError), escape(formatString), LEADING_COMMA_PLACEHOLDER.repeat(argCount));
    }

    static String escape(String formatString) {
        return formatString.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
