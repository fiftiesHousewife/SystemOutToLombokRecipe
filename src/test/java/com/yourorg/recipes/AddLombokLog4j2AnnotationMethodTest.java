package com.yourorg.recipes;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.SourceFile;
import org.openrewrite.java.tree.J;

import static org.assertj.core.api.Assertions.assertThat;

class AddLombokLog4j2AnnotationMethodTest {

    private final JavaParser javaParser = JavaParser.fromJavaVersion().build();
    private final ExecutionContext ctx = new InMemoryExecutionContext();

    @Test
    void isLombokLoggingAnnotation_detectsLog4j2() {
        String code = """
                package com.example;
                import lombok.extern.log4j.Log4j2;

                @Log4j2
                public class Test {}
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertThat(visitor.foundLog4j2Annotation).isTrue();
    }

    @Test
    void isLombokLoggingAnnotation_detectsSlf4j() {
        String code = """
                package com.example;
                import lombok.extern.slf4j.Slf4j;

                @Slf4j
                public class Test {}
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertThat(visitor.foundLombokAnnotation).isTrue();
    }

    @Test
    void isLombokLoggingAnnotation_detectsLog4j() {
        String code = """
                package com.example;
                import lombok.extern.log4j.Log4j;

                @Log4j
                public class Test {}
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertThat(visitor.foundLombokAnnotation).isTrue();
    }

    @Test
    void hasExplicitLoggerField_detectsLogField() {
        String code = """
                package com.example;
                import org.apache.logging.log4j.Logger;

                public class Test {
                    private static final Logger log = null;
                }
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertThat(visitor.hasExplicitLogger).isTrue();
    }

    @Test
    void hasExplicitLoggerField_detectsLoggerField() {
        String code = """
                package com.example;

                public class Test {
                    private Object logger;
                }
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertThat(visitor.hasExplicitLogger).isTrue();
    }

    @Test
    void hasExplicitLoggerField_detectsLOGField() {
        String code = """
                package com.example;

                public class Test {
                    private static final Object LOG = null;
                }
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertThat(visitor.hasExplicitLogger).isTrue();
    }

    @Test
    void hasExplicitLoggerField_detectsLOGGERField() {
        String code = """
                package com.example;

                public class Test {
                    private static Object LOGGER;
                }
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertThat(visitor.hasExplicitLogger).isTrue();
    }

    @Test
    void hasExplicitLoggerField_returnsFalseForOtherFields() {
        String code = """
                package com.example;

                public class Test {
                    private String name;
                    private int count;
                }
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertThat(visitor.hasExplicitLogger).isFalse();
    }

    @Test
    void containsSystemOutCalls_detectsSystemOut() {
        String code = """
                package com.example;

                public class Test {
                    public void method() {
                        System.out.println("test");
                    }
                }
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertThat(visitor.hasSystemOut).isTrue();
    }

    @Test
    void containsSystemOutCalls_detectsSystemErr() {
        String code = """
                package com.example;

                public class Test {
                    public void method() {
                        System.err.println("error");
                    }
                }
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertThat(visitor.hasSystemOut).isTrue();
    }

    @Test
    void containsSystemOutCalls_returnsFalseWhenNoSystemOut() {
        String code = """
                package com.example;

                public class Test {
                    public void method() {
                        String message = "test";
                    }
                }
                """;

        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertThat(visitor.hasSystemOut).isFalse();
    }

    private static class TestVisitor extends JavaIsoVisitor<ExecutionContext> {
        boolean foundLog4j2Annotation = false;
        boolean foundLombokAnnotation = false;
        boolean hasExplicitLogger = false;
        boolean hasSystemOut = false;

        AddLombokLog4j2Annotation recipe = new AddLombokLog4j2Annotation();

        @Override
        @SuppressWarnings("unchecked")
        @NullMarked
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            JavaIsoVisitor<ExecutionContext> recipeVisitor = (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

            hasSystemOut = testContainsSystemOut(recipeVisitor, classDecl);

            for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
                boolean isLombok = testIsLombokLoggingAnnotation(recipeVisitor, annotation);
                if (isLombok) {
                    foundLombokAnnotation = true;
                    if (annotation.getSimpleName().equals("Log4j2")) {
                        foundLog4j2Annotation = true;
                    }
                }
            }

            hasExplicitLogger = testHasExplicitLoggerField(recipeVisitor, classDecl);

            return super.visitClassDeclaration(classDecl, ctx);
        }

        @SuppressWarnings( "DataFlowIssue")
        private boolean testContainsSystemOut(JavaIsoVisitor<ExecutionContext> visitor, J.ClassDeclaration classDecl) {
            try {
                var method = visitor.getClass().getDeclaredMethod("containsSystemOutCalls", J.ClassDeclaration.class);
                method.setAccessible(true);
                return (boolean) method.invoke(visitor, classDecl);
            } catch (Exception e) {
                HasSystemOutChecker checker = new HasSystemOutChecker();
                checker.visit(classDecl, null);
                return checker.found;
            }
        }

        private boolean testIsLombokLoggingAnnotation(JavaIsoVisitor<ExecutionContext> visitor, J.Annotation annotation) {
            try {
                var method = visitor.getClass().getDeclaredMethod("isLombokLoggingAnnotation", J.Annotation.class);
                method.setAccessible(true);
                return (boolean) method.invoke(visitor, annotation);
            } catch (Exception e) {
                String name = annotation.getSimpleName();
                return name.equals("Slf4j") || name.equals("Log4j") || name.equals("Log4j2");
            }
        }

        private boolean testHasExplicitLoggerField(JavaIsoVisitor<ExecutionContext> visitor, J.ClassDeclaration classDecl) {
            try {
                var method = visitor.getClass().getDeclaredMethod("hasExplicitLoggerField", J.ClassDeclaration.class);
                method.setAccessible(true);
                return (boolean) method.invoke(visitor, classDecl);
            } catch (Exception e) {
                return classDecl.getBody().getStatements().stream()
                        .filter(J.VariableDeclarations.class::isInstance)
                        .map(J.VariableDeclarations.class::cast)
                        .flatMap(vd -> vd.getVariables().stream())
                        .anyMatch(v -> {
                            String name = v.getSimpleName();
                            return "log".equals(name) || "logger".equals(name) ||
                                   "LOG".equals(name) || "LOGGER".equals(name);
                        });
            }
        }
    }

    private static class HasSystemOutChecker extends JavaIsoVisitor<Void> {
        boolean found = false;

        @Override
        @NullMarked
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Void unused) {
            if (method.getSelect() != null) {
                String select = method.getSelect().toString();
                if (select.equals("System.out") || select.equals("System.err")) {
                    found = true;
                }
            }
            return super.visitMethodInvocation(method, unused);
        }
    }
}