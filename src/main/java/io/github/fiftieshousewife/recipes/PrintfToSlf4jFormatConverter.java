package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;

/**
 * Converts a Java {@code printf}-style format string into the {@code {}}-style
 * format expected by Log4j2. Handles the full grammar of specifiers
 * (argument index, flags, width, precision, conversion char, including the
 * {@code %t/%T} date-time pair).
 *
 * <p>Stateless. All methods are pure static utilities and unit-testable
 * directly.
 */
@NullMarked
final class PrintfToSlf4jFormatConverter {

    private static final String FLAG_CHARS = "+-0 #,(";

    private PrintfToSlf4jFormatConverter() {
    }

    static String convert(final String printfFormat) {
        final StringBuilder result = new StringBuilder();
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
            final char specifier = printfFormat.charAt(i);
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
        final int mark = i;
        while (i < format.length() && Character.isDigit(format.charAt(i))) {
            i++;
        }
        if (i < format.length() && format.charAt(i) == '$') {
            return i + 1;
        }
        return mark;
    }

    static int skipFlags(String format, int i) {
        while (i < format.length() && FLAG_CHARS.indexOf(format.charAt(i)) >= 0) {
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
        final char conv = format.charAt(i++);
        if (isDateTimeConversion(conv) && i < format.length()) {
            i++;
        }
        return i;
    }

    static boolean isDateTimeConversion(char conv) {
        return conv == 't' || conv == 'T';
    }
}
