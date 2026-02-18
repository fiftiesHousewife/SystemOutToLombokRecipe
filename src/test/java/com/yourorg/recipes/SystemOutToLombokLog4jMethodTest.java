package com.yourorg.recipes;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.SourceFile;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemOutToLombokLog4jMethodTest {

    private final JavaParser javaParser = JavaParser.fromJavaVersion().build();
    private final ExecutionContext ctx = new InMemoryExecutionContext();

    @Test
    @SuppressWarnings("unchecked")
    void getLogLevel_returnsInfoForNonError() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        String level = invokeGetLogLevel(visitor, false);
        assertThat(level).isEqualTo("info");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLogLevel_returnsErrorForError() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        String level = invokeGetLogLevel(visitor, true);
        assertThat(level).isEqualTo("error");
    }

    @Test
    @SuppressWarnings("unchecked")
    void escapeFormatString_escapesBackslashes() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        String result = invokeEscapeFormatString(visitor, "C:\\path\\to\\file");
        assertThat(result).isEqualTo("C:\\\\path\\\\to\\\\file");
    }

    @Test
    @SuppressWarnings("unchecked")
    void escapeFormatString_escapesQuotes() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        String result = invokeEscapeFormatString(visitor, "Say \"hello\"");
        assertThat(result).isEqualTo("Say \\\"hello\\\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void escapeFormatString_escapesBoth() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        String result = invokeEscapeFormatString(visitor, "Path: \"C:\\test\"");
        assertThat(result).isEqualTo("Path: \\\"C:\\\\test\\\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildLogCallTemplate_singleArg() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        String template = invokeBuildLogCallTemplate(visitor, 1, false);
        assertThat(template).isEqualTo("log.info(#{any()})");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildLogCallTemplate_multipleArgs() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        String template = invokeBuildLogCallTemplate(visitor, 3, false);
        assertThat(template).isEqualTo("log.info(#{any()}, #{any()}, #{any()})");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildLogCallTemplate_errorLevel() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        String template = invokeBuildLogCallTemplate(visitor, 2, true);
        assertThat(template).isEqualTo("log.error(#{any()}, #{any()})");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildParameterizedLogTemplate_noArgs() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        String template = invokeBuildParameterizedLogTemplate(visitor, "Message", 0, false);
        assertThat(template).isEqualTo("log.info(\"Message\")");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildParameterizedLogTemplate_withArgs() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        String template = invokeBuildParameterizedLogTemplate(visitor, "Value: {}", 1, false);
        assertThat(template).isEqualTo("log.info(\"Value: {}\", #{any()})");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildParameterizedLogTemplate_errorWithMultipleArgs() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        String template = invokeBuildParameterizedLogTemplate(visitor, "x={}, y={}", 2, true);
        assertThat(template).isEqualTo("log.error(\"x={}, y={}\", #{any()}, #{any()})");
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
    void isStringConcatenation_detectsConcatenation() {
        String code = """
                package com.example;
                public class Test {
                    void method() {
                        String x = "a" + "b";
                    }
                }
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        ConcatenationFinder finder = new ConcatenationFinder();
        finder.visit(cu, ctx);

        assertThat(finder.foundConcatenation).isTrue();
    }

    @Test
    @SuppressWarnings({"unchecked", "DataFlowIssue"})
    void buildFormatString_handlesLiterals() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        List<Expression> parts = new ArrayList<>();
        parts.add(new J.Literal(null, null, null, "Hello ", "\"Hello \"", null, null));

        String result = invokeBuildFormatString(visitor, parts);
        assertThat(result).isEqualTo("Hello ");
    }

    @Test
    @SuppressWarnings({"unchecked", "DataFlowIssue"})
    void buildFormatString_handlesPlaceholders() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        List<Expression> parts = new ArrayList<>();
        parts.add(new J.Literal(null, null, null, "Value: ", "\"Value: \"", null, null));
        parts.add(new J.Identifier(null, null, null, null, "x", null, null));

        String result = invokeBuildFormatString(visitor, parts);
        assertThat(result).isEqualTo("Value: {}");
    }

    @Test
    @SuppressWarnings({"unchecked", "DataFlowIssue"})
    void extractNonLiteralArguments_filtersLiterals() {
        SystemOutToLombokLog4j recipe = new SystemOutToLombokLog4j();
        JavaIsoVisitor<ExecutionContext> visitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        List<Expression> parts = new ArrayList<>();
        J.Literal literal = new J.Literal(null, null, null, "text", "\"text\"", null, null);
        J.Identifier nonLiteral = new J.Identifier(null, null, null, null, "x", null, null);

        parts.add(literal);
        parts.add(nonLiteral);

        List<Expression> result = invokeExtractNonLiteralArguments(visitor, parts);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(nonLiteral);
    }

    private String invokeGetLogLevel(JavaIsoVisitor<ExecutionContext> visitor, boolean isError) {
        try {
            var method = visitor.getClass().getDeclaredMethod("getLogLevel", boolean.class);
            method.setAccessible(true);
            return (String) method.invoke(visitor, isError);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String invokeEscapeFormatString(JavaIsoVisitor<ExecutionContext> visitor, String input) {
        try {
            var method = visitor.getClass().getDeclaredMethod("escapeFormatString", String.class);
            method.setAccessible(true);
            return (String) method.invoke(visitor, input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String invokeBuildLogCallTemplate(JavaIsoVisitor<ExecutionContext> visitor, int argCount, boolean isError) {
        try {
            var method = visitor.getClass().getDeclaredMethod("buildLogCallTemplate", int.class, boolean.class);
            method.setAccessible(true);
            return (String) method.invoke(visitor, argCount, isError);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String invokeBuildParameterizedLogTemplate(JavaIsoVisitor<ExecutionContext> visitor, String format, int argCount, boolean isError) {
        try {
            var method = visitor.getClass().getDeclaredMethod("buildParameterizedLogTemplate", String.class, int.class, boolean.class);
            method.setAccessible(true);
            return (String) method.invoke(visitor, format, argCount, isError);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String invokeBuildFormatString(JavaIsoVisitor<ExecutionContext> visitor, List<Expression> parts) {
        try {
            var method = visitor.getClass().getDeclaredMethod("buildFormatString", List.class);
            method.setAccessible(true);
            return (String) method.invoke(visitor, parts);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Expression> invokeExtractNonLiteralArguments(JavaIsoVisitor<ExecutionContext> visitor, List<Expression> parts) {
        try {
            var method = visitor.getClass().getDeclaredMethod("extractNonLiteralArguments", List.class);
            method.setAccessible(true);
            return (List<Expression>) method.invoke(visitor, parts);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class MethodInvocationFinder extends JavaIsoVisitor<ExecutionContext> {
        boolean foundSystemOut = false;
        boolean foundSystemErr = false;

        @Override
        @NullMarked
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (method.getSelect() != null) {
                String select = method.getSelect().toString();
                if (select.equals("System.out")) {
                    foundSystemOut = true;
                }
                if (select.equals("System.err")) {
                    foundSystemErr = true;
                }
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }

    private static class ConcatenationFinder extends JavaIsoVisitor<ExecutionContext> {
        boolean foundConcatenation = false;

        @Override
        @NullMarked
        public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
            if (binary.getOperator() == J.Binary.Type.Addition) {
                foundConcatenation = true;
            }
            return super.visitBinary(binary, ctx);
        }
    }
}
