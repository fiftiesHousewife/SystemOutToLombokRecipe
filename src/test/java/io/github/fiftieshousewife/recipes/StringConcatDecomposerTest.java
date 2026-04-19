package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

import static io.github.fiftieshousewife.recipes.StringConcatDecomposer.formatString;
import static io.github.fiftieshousewife.recipes.StringConcatDecomposer.nonLiterals;
import static org.assertj.core.api.Assertions.assertThat;

class StringConcatDecomposerTest {

    @Test
    @SuppressWarnings("DataFlowIssue")
    void formatString_handlesLiterals() {
        List<Expression> parts = new ArrayList<>();
        parts.add(new J.Literal(null, null, null, "Hello ", "\"Hello \"", null, null));
        assertThat(formatString(parts)).isEqualTo("Hello ");
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void formatString_handlesPlaceholders() {
        List<Expression> parts = new ArrayList<>();
        parts.add(new J.Literal(null, null, null, "Value: ", "\"Value: \"", null, null));
        parts.add(new J.Identifier(null, null, null, null, "x", null, null));
        assertThat(formatString(parts)).isEqualTo("Value: {}");
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void nonLiterals_filtersLiterals() {
        List<Expression> parts = new ArrayList<>();
        J.Literal literal = new J.Literal(null, null, null, "text", "\"text\"", null, null);
        J.Identifier nonLiteral = new J.Identifier(null, null, null, null, "x", null, null);
        parts.add(literal);
        parts.add(nonLiteral);

        List<Expression> result = nonLiterals(parts);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(nonLiteral);
    }
}
