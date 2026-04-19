package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import static io.github.fiftieshousewife.recipes.SystemOutToLombokLog4j.isSystemErr;
import static io.github.fiftieshousewife.recipes.SystemOutToLombokLog4j.isSystemOutOrErr;
import static org.assertj.core.api.Assertions.assertThat;

class SystemOutToLombokLog4jMethodTest {

    private final JavaParser javaParser = JavaParser.fromJavaVersion().build();
    private final ExecutionContext ctx = new InMemoryExecutionContext();

    @Test
    void isSystemOutOrErr_detectsSystemOut() {
        MethodInvocationFinder finder = visit("""
                package com.example;
                public class Test {
                    void method() {
                        System.out.println("test");
                    }
                }
                """);
        assertThat(finder.foundSystemOut).isTrue();
    }

    @Test
    void isSystemErr_detectsSystemErr() {
        MethodInvocationFinder finder = visit("""
                package com.example;
                public class Test {
                    void method() {
                        System.err.println("error");
                    }
                }
                """);
        assertThat(finder.foundSystemErr).isTrue();
    }

    private MethodInvocationFinder visit(String code) {
        SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        MethodInvocationFinder finder = new MethodInvocationFinder();
        finder.visit(cu, ctx);
        return finder;
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
