package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.Optional;

@NullMarked
enum PrintMethod {
    PRINTLN("println", new MethodMatcher("java.io.PrintStream println(..)"), SystemOutVisitor::replacePrintln),
    PRINT("print", new MethodMatcher("java.io.PrintStream print(..)"), SystemOutVisitor::replacePrint),
    PRINTF("printf", new MethodMatcher("java.io.PrintStream printf(..)"), SystemOutVisitor::replacePrintf);

    @FunctionalInterface
    interface Replacer {
        J.MethodInvocation apply(SystemOutVisitor visitor, J.MethodInvocation method, boolean isError);
    }

    private final String simpleName;
    private final MethodMatcher matcher;
    private final Replacer replacer;

    PrintMethod(final String simpleName, final MethodMatcher matcher, final Replacer replacer) {
        this.simpleName = simpleName;
        this.matcher = matcher;
        this.replacer = replacer;
    }

    boolean matches(final J.MethodInvocation method) {
        return simpleName.equals(method.getSimpleName()) && matcher.matches(method);
    }

    J.MethodInvocation apply(final SystemOutVisitor visitor, final J.MethodInvocation method, final boolean isError) {
        return replacer.apply(visitor, method, isError);
    }

    static Optional<PrintMethod> forCall(final J.MethodInvocation method) {
        return Arrays.stream(values()).filter(p -> p.matches(method)).findFirst();
    }
}
