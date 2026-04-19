package org.fifties.housewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fifties.housewife.recipes.SystemOutToLombokLog4j.*;

class SystemOutToLombokLog4jMethodTest {

    private final JavaParser javaParser = JavaParser.fromJavaVersion().build();
    private final ExecutionContext ctx = new InMemoryExecutionContext();

    @Test
    void getLogLevel_returnsInfoForNonError() {
        assertThat(getLogLevel(false)).isEqualTo("info");
    }

    @Test
    void getLogLevel_returnsErrorForError() {
        assertThat(getLogLevel(true)).isEqualTo("error");
    }

    @Test
    void escapeFormatString_escapesBackslashes() {
        assertThat(escapeFormatString("C:\\path\\to\\file")).isEqualTo("C:\\\\path\\\\to\\\\file");
    }

    @Test
    void escapeFormatString_escapesQuotes() {
        assertThat(escapeFormatString("Say \"hello\"")).isEqualTo("Say \\\"hello\\\"");
    }

    @Test
    void escapeFormatString_escapesBoth() {
        assertThat(escapeFormatString("Path: \"C:\\test\"")).isEqualTo("Path: \\\"C:\\\\test\\\"");
    }

    @Test
    void buildLogCallTemplate_singleArg() {
        assertThat(buildLogCallTemplate(1, false)).isEqualTo("log.info(#{any()})");
    }

    @Test
    void buildLogCallTemplate_multipleArgs() {
        assertThat(buildLogCallTemplate(3, false)).isEqualTo("log.info(#{any()}, #{any()}, #{any()})");
    }

    @Test
    void buildLogCallTemplate_errorLevel() {
        assertThat(buildLogCallTemplate(2, true)).isEqualTo("log.error(#{any()}, #{any()})");
    }

    @Test
    void buildParameterizedLogTemplate_noArgs() {
        assertThat(buildParameterizedLogTemplate("Message", 0, false)).isEqualTo("log.info(\"Message\")");
    }

    @Test
    void buildParameterizedLogTemplate_withArgs() {
        assertThat(buildParameterizedLogTemplate("Value: {}", 1, false)).isEqualTo("log.info(\"Value: {}\", #{any()})");
    }

    @Test
    void buildParameterizedLogTemplate_errorWithMultipleArgs() {
        assertThat(buildParameterizedLogTemplate("x={}, y={}", 2, true))
                .isEqualTo("log.error(\"x={}, y={}\", #{any()}, #{any()})");
    }

    @Test
    void isSystemOutOrErr_detectsSystemOut() {
        String code = """
                package com.example;
                public class Test {
                    void method() {
                        System.out.println("test");
                    }
                }
                """;
        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        MethodInvocationFinder finder = new MethodInvocationFinder();
        finder.visit(cu, ctx);
        assertThat(finder.foundSystemOut).isTrue();
    }

    @Test
    void isSystemErr_detectsSystemErr() {
        String code = """
                package com.example;
                public class Test {
                    void method() {
                        System.err.println("error");
                    }
                }
                """;
        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        MethodInvocationFinder finder = new MethodInvocationFinder();
        finder.visit(cu, ctx);
        assertThat(finder.foundSystemErr).isTrue();
    }

@Test
    @SuppressWarnings("DataFlowIssue")
    void buildFormatString_handlesLiterals() {
        List<Expression> parts = new ArrayList<>();
        parts.add(new J.Literal(null, null, null, "Hello ", "\"Hello \"", null, null));
        assertThat(buildFormatString(parts)).isEqualTo("Hello ");
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void buildFormatString_handlesPlaceholders() {
        List<Expression> parts = new ArrayList<>();
        parts.add(new J.Literal(null, null, null, "Value: ", "\"Value: \"", null, null));
        parts.add(new J.Identifier(null, null, null, null, "x", null, null));
        assertThat(buildFormatString(parts)).isEqualTo("Value: {}");
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void extractNonLiteralArguments_filtersLiterals() {
        List<Expression> parts = new ArrayList<>();
        J.Literal literal = new J.Literal(null, null, null, "text", "\"text\"", null, null);
        J.Identifier nonLiteral = new J.Identifier(null, null, null, null, "x", null, null);
        parts.add(literal);
        parts.add(nonLiteral);

        List<Expression> result = extractNonLiteralArguments(parts);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(nonLiteral);
    }

    @Test
    void convertPrintfToLog4jFormat_singleSpecifier() {
        assertThat(convertPrintfToLog4jFormat("Value: %d%n")).isEqualTo("Value: {}");
    }

    @Test
    void convertPrintfToLog4jFormat_multipleSpecifiers() {
        assertThat(convertPrintfToLog4jFormat("Name: %s, Age: %d%n")).isEqualTo("Name: {}, Age: {}");
    }

    @Test
    void convertPrintfToLog4jFormat_escapedPercent() {
        assertThat(convertPrintfToLog4jFormat("100%%")).isEqualTo("100%");
    }

    @Test
    void convertPrintfToLog4jFormat_noSpecifiers() {
        assertThat(convertPrintfToLog4jFormat("Simple message")).isEqualTo("Simple message");
    }

    @Test
    void convertPrintfToLog4jFormat_newlineOnly() {
        assertThat(convertPrintfToLog4jFormat("Hello%n")).isEqualTo("Hello");
    }

    @Test
    void skipArgumentIndex_consumesIndexAndDollar() {
        assertThat(skipArgumentIndex("1$s", 0)).isEqualTo(2);
    }

    @Test
    void skipArgumentIndex_consumesMultiDigitIndex() {
        assertThat(skipArgumentIndex("12$s", 0)).isEqualTo(3);
    }

    @Test
    void skipArgumentIndex_doesNotConsumeWhenNoDollar() {
        assertThat(skipArgumentIndex("s", 0)).isEqualTo(0);
    }

    @Test
    void skipFlags_consumesFlags() {
        assertThat(skipFlags("-+s", 0)).isEqualTo(2);
    }

    @Test
    void skipFlags_doesNotConsumeNonFlags() {
        assertThat(skipFlags("s", 0)).isEqualTo(0);
    }

    @Test
    void skipWidth_consumesDigits() {
        assertThat(skipWidth("10s", 0)).isEqualTo(2);
    }

    @Test
    void skipWidth_doesNotConsumeNonDigits() {
        assertThat(skipWidth("s", 0)).isEqualTo(0);
    }

    @Test
    void skipPrecision_consumesDotAndDigits() {
        assertThat(skipPrecision(".5f", 0)).isEqualTo(2);
    }

    @Test
    void skipPrecision_doesNotConsumeWhenNoDot() {
        assertThat(skipPrecision("f", 0)).isEqualTo(0);
    }

    @Test
    void skipConversionChar_consumesSingleChar() {
        assertThat(skipConversionChar("s", 0)).isEqualTo(1);
    }

    @Test
    void skipConversionChar_consumesTwoCharsForDateTime() {
        assertThat(skipConversionChar("tH", 0)).isEqualTo(2);
    }

    @Test
    void skipConversionChar_handlesEmptyInput() {
        assertThat(skipConversionChar("", 0)).isEqualTo(0);
    }

    @Test
    void isDateTimeConversion_trueForLowercaseT() {
        assertThat(isDateTimeConversion('t')).isTrue();
    }

    @Test
    void isDateTimeConversion_trueForUppercaseT() {
        assertThat(isDateTimeConversion('T')).isTrue();
    }

    @Test
    void isDateTimeConversion_falseForOtherChar() {
        assertThat(isDateTimeConversion('s')).isFalse();
    }

    @Test
    void skipSpecifier_simpleConversion() {
        assertThat(skipSpecifier("s", 0)).isEqualTo(1);
    }

    @Test
    void skipSpecifier_withWidthAndPrecision() {
        assertThat(skipSpecifier("10.5s", 0)).isEqualTo(5);
    }

    @Test
    void skipSpecifier_withAllParts() {
        assertThat(skipSpecifier("1$-10.5s", 0)).isEqualTo(8);
    }

    private static class MethodInvocationFinder extends JavaIsoVisitor<ExecutionContext> {
        boolean foundSystemOut;
        boolean foundSystemErr;

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (isSystemOutOrErr(method)) {
                if (isSystemErr(method)) {
                    foundSystemErr = true;
                } else {
                    foundSystemOut = true;
                }
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }

}
