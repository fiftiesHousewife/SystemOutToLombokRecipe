package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Decomposes a string-concatenation expression tree
 * (e.g. {@code "x = " + x + ", y = " + y}) into a parameterized-log format
 * string (e.g. {@code "x = {}, y = {}"}) plus the list of non-literal arguments
 * to substitute.
 */
@NullMarked
final class StringConcatDecomposer {

    private StringConcatDecomposer() {
    }

    static List<Expression> flatten(final J.Binary binary) {
        final List<Expression> parts = new ArrayList<>();
        parts.addAll(flattenSide(binary.getLeft()));
        parts.addAll(flattenSide(binary.getRight()));
        return parts;
    }

    static String formatString(final List<Expression> parts) {
        return parts.stream()
                .map(StringConcatDecomposer::formatPart)
                .collect(Collectors.joining());
    }

    static List<Expression> nonLiterals(final List<Expression> parts) {
        return parts.stream()
                .filter(part -> !(part instanceof J.Literal))
                .toList();
    }

    private static String formatPart(final Expression part) {
        if (part instanceof J.Literal literal && literal.getValue() != null) {
            return literal.getValue().toString();
        }
        return "{}";
    }

    private static List<Expression> flattenSide(final Expression side) {
        if (side instanceof J.Binary childBinary && childBinary.getOperator() == J.Binary.Type.Addition) {
            return flatten(childBinary);
        }
        return List.of(side);
    }
}
