package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import static org.assertj.core.api.Assertions.assertThat;

class AddLombokLog4j2AnnotationMethodTest {

    private final JavaParser javaParser = JavaParser.fromJavaVersion().build();
    private final ExecutionContext ctx = new InMemoryExecutionContext();

    @Test
    void isLombokLoggingAnnotation_detectsLog4j2() {
        assertClassProperty("""
                package com.example;
                import lombok.extern.log4j.Log4j2;

                @Log4j2
                public class Test {}
                """, c -> AddLombokLog4j2Annotation.hasLombokLoggingAnnotation(c));
    }

    @Test
    void isLombokLoggingAnnotation_detectsSlf4j() {
        assertClassProperty("""
                package com.example;
                import lombok.extern.slf4j.Slf4j;

                @Slf4j
                public class Test {}
                """, c -> AddLombokLog4j2Annotation.hasLombokLoggingAnnotation(c));
    }

    @Test
    void isLombokLoggingAnnotation_detectsLog4j() {
        assertClassProperty("""
                package com.example;
                import lombok.extern.log4j.Log4j;

                @Log4j
                public class Test {}
                """, c -> AddLombokLog4j2Annotation.hasLombokLoggingAnnotation(c));
    }

    @Test
    void hasExplicitLoggerField_detectsLogField() {
        assertClassProperty("""
                package com.example;
                import org.apache.logging.log4j.Logger;

                public class Test {
                    private static final Logger log = null;
                }
                """, AddLombokLog4j2Annotation::hasExplicitLoggerField);
    }

    @Test
    void hasExplicitLoggerField_detectsLoggerField() {
        assertClassProperty("""
                package com.example;

                public class Test {
                    private Object logger;
                }
                """, AddLombokLog4j2Annotation::hasExplicitLoggerField);
    }

    @Test
    void hasExplicitLoggerField_detectsLOGField() {
        assertClassProperty("""
                package com.example;

                public class Test {
                    private static final Object LOG = null;
                }
                """, AddLombokLog4j2Annotation::hasExplicitLoggerField);
    }

    @Test
    void hasExplicitLoggerField_detectsLOGGERField() {
        assertClassProperty("""
                package com.example;

                public class Test {
                    private static Object LOGGER;
                }
                """, AddLombokLog4j2Annotation::hasExplicitLoggerField);
    }

    @Test
    void hasExplicitLoggerField_returnsFalseForOtherFields() {
        refuteClassProperty("""
                package com.example;

                public class Test {
                    private String name;
                    private int count;
                }
                """, AddLombokLog4j2Annotation::hasExplicitLoggerField);
    }

    @Test
    void containsSystemOutCalls_detectsSystemOut() {
        assertClassProperty("""
                package com.example;

                public class Test {
                    public void method() {
                        System.out.println("test");
                    }
                }
                """, AddLombokLog4j2Annotation::containsSystemOutCalls);
    }

    @Test
    void containsSystemOutCalls_detectsSystemErr() {
        assertClassProperty("""
                package com.example;

                public class Test {
                    public void method() {
                        System.err.println("error");
                    }
                }
                """, AddLombokLog4j2Annotation::containsSystemOutCalls);
    }

    @Test
    void containsSystemOutCalls_returnsFalseWhenNoSystemOut() {
        refuteClassProperty("""
                package com.example;

                public class Test {
                    public void method() {
                        String message = "test";
                    }
                }
                """, AddLombokLog4j2Annotation::containsSystemOutCalls);
    }

    private void assertClassProperty(String code, java.util.function.Predicate<J.ClassDeclaration> predicate) {
        assertThat(evaluate(code, predicate)).isTrue();
    }

    private void refuteClassProperty(String code, java.util.function.Predicate<J.ClassDeclaration> predicate) {
        assertThat(evaluate(code, predicate)).isFalse();
    }

    private boolean evaluate(String code, java.util.function.Predicate<J.ClassDeclaration> predicate) {
        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        ClassDeclarationCollector collector = new ClassDeclarationCollector();
        collector.visit(cu, ctx);
        return collector.result != null && predicate.test(collector.result);
    }

    private static class ClassDeclarationCollector extends JavaIsoVisitor<ExecutionContext> {
        J.ClassDeclaration result;

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            if (result == null) {
                result = classDecl;
            }
            return super.visitClassDeclaration(classDecl, ctx);
        }
    }
}
