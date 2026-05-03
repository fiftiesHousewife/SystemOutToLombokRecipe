package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static io.github.fiftieshousewife.recipes.SystemOutToSlf4j.hasNoRealArg;
import static io.github.fiftieshousewife.recipes.SystemOutToSlf4j.isSystemErr;
import static io.github.fiftieshousewife.recipes.SystemOutToSlf4j.isSystemOutOrErr;
import static org.assertj.core.api.Assertions.assertThat;

class SystemOutToSlf4jMethodTest {

    private final JavaParser javaParser = JavaParser.fromJavaVersion().build();
    private final ExecutionContext ctx = new InMemoryExecutionContext();

    @Test
    void isSystemOutOrErr_detectsSystemOut() {
        final MethodInvocationFinder finder = visit("""
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
        final MethodInvocationFinder finder = visit("""
                package com.example;
                public class Test {
                    void method() {
                        System.err.println("error");
                    }
                }
                """);
        assertThat(finder.foundSystemErr).isTrue();
    }

    @Test
    void hasNoRealArg_trueForEmptyList() {
        assertThat(hasNoRealArg(Collections.emptyList())).isTrue();
    }

    @Test
    void hasNoRealArg_trueForSingleEmpty() {
        final List<org.openrewrite.java.tree.Expression> args = new ArrayList<>();
        firstMethodInvocation("""
                package com.example;
                public class T { void m() { System.out.println(); } }
                """, mi -> args.addAll(mi.getArguments()));
        assertThat(hasNoRealArg(args)).isTrue();
    }

    @Test
    void hasNoRealArg_falseForNonEmptyArg() {
        final List<org.openrewrite.java.tree.Expression> args = new ArrayList<>();
        firstMethodInvocation("""
                package com.example;
                public class T { void m() { System.out.println("hi"); } }
                """, mi -> args.addAll(mi.getArguments()));
        assertThat(hasNoRealArg(args)).isFalse();
    }

    @Test
    void printMethod_forCall_findsPrintln() {
        assertThat(printMethodOf("System.out.println(\"x\")")).hasValue(PrintMethod.PRINTLN);
    }

    @Test
    void printMethod_forCall_findsPrint() {
        assertThat(printMethodOf("System.out.print(\"x\")")).hasValue(PrintMethod.PRINT);
    }

    @Test
    void printMethod_forCall_findsPrintf() {
        assertThat(printMethodOf("System.out.printf(\"%s\", \"x\")")).hasValue(PrintMethod.PRINTF);
    }

    @Test
    void printMethod_forCall_emptyForUnknownMethod() {
        assertThat(printMethodOf("System.out.flush()")).isEmpty();
    }

    @Test
    void printMethod_matches_falseForMismatchedName() {
        final J.MethodInvocation flush = captureMethodInvocation("System.out.flush()");
        assertThat(PrintMethod.PRINTLN.matches(flush)).isFalse();
    }

    private Optional<PrintMethod> printMethodOf(String call) {
        return PrintMethod.forCall(captureMethodInvocation(call));
    }

    private J.MethodInvocation captureMethodInvocation(String call) {
        final J.MethodInvocation[] captured = {null};
        firstMethodInvocation(
                "package com.example;\npublic class T { void m() { " + call + "; } }\n",
                mi -> captured[0] = mi);
        return captured[0];
    }

    private void firstMethodInvocation(String code, Consumer<J.MethodInvocation> sink) {
        final SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        new JavaIsoVisitor<ExecutionContext>() {
            boolean done;
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (!done) {
                    done = true;
                    sink.accept(method);
                }
                return super.visitMethodInvocation(method, ctx);
            }
        }.visit(cu, ctx);
    }

    private MethodInvocationFinder visit(String code) {
        final SourceFile cu = javaParser.parse(code).findFirst().orElseThrow();
        final MethodInvocationFinder finder = new MethodInvocationFinder();
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
