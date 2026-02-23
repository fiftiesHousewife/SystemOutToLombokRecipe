package com.yourorg.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

/**
 * Adds the Lombok @Log4j2 annotation to classes that use System.out print statements or printStackTrace calls.
 * This recipe should be applied to classes before converting System.out to log statements.
 */
@NullMarked
public class AddLombokLog4j2Annotation extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add Lombok @Log4j2 annotation to classes";
    }

    @Override
    public String getDescription() {
        return "Adds the Lombok @Log4j2 annotation to classes that contain System.out print statements, " +
                "enabling the use of a 'log' field for logging.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (!containsSystemOutCalls(classDecl)) {
                    return classDecl;
                }

                if (hasLombokLoggingAnnotation(classDecl)) {
                    return classDecl;
                }

                if (hasExplicitLoggerField(classDecl)) {
                    return classDecl;
                }

                return addLog4j2Annotation(classDecl);
            }

            boolean containsSystemOutCalls(J.ClassDeclaration classDecl) {
                HasSystemOutVisitor visitor = new HasSystemOutVisitor();
                visitor.visit(classDecl, false);
                return visitor.hasSystemOut();
            }

            boolean hasLombokLoggingAnnotation(J.ClassDeclaration classDecl) {
                return classDecl.getLeadingAnnotations().stream()
                        .anyMatch(this::isLombokLoggingAnnotation);
            }

            boolean isLombokLoggingAnnotation(J.Annotation annotation) {
                String simpleName = annotation.getSimpleName();
                if (simpleName.equals("Slf4j") || simpleName.equals("Log4j") ||
                    simpleName.equals("Log4j2") || simpleName.equals("Log") ||
                    simpleName.equals("CommonsLog") || simpleName.equals("Flogger") ||
                    simpleName.equals("JBossLog") || simpleName.equals("CustomLog")) {
                    return true;
                }

                if (annotation.getType() == null) {
                    return false;
                }
                String typeName = annotation.getType().toString();
                return typeName.contains("lombok.extern.slf4j.Slf4j")
                        || typeName.contains("lombok.extern.log4j.Log4j")
                        || typeName.contains("lombok.extern.log4j.Log4j2")
                        || typeName.contains("lombok.extern.java.Log")
                        || typeName.contains("lombok.extern.apachecommons.CommonsLog")
                        || typeName.contains("lombok.extern.flogger.Flogger")
                        || typeName.contains("lombok.extern.jbosslog.JBossLog")
                        || typeName.contains("lombok.CustomLog");
            }

            boolean hasExplicitLoggerField(J.ClassDeclaration classDecl) {
                return classDecl.getBody().getStatements().stream()
                        .filter(J.VariableDeclarations.class::isInstance)
                        .map(J.VariableDeclarations.class::cast)
                        .anyMatch(this::isLoggerVariable);
            }

            boolean isLoggerVariable(J.VariableDeclarations variableDeclarations) {
                return variableDeclarations.getVariables().stream()
                        .anyMatch(var -> "log".equals(var.getSimpleName())
                                || "logger".equals(var.getSimpleName())
                                || "LOG".equals(var.getSimpleName())
                                || "LOGGER".equals(var.getSimpleName()));
            }

            J.ClassDeclaration addLog4j2Annotation(J.ClassDeclaration classDecl) {
                maybeAddImport("lombok.extern.log4j.Log4j2", null, false);

                return JavaTemplate.builder("@Log4j2")
                        .imports("lombok.extern.log4j.Log4j2")
                        .build()
                        .apply(
                                getCursor(),
                                classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                        );
            }
        };
    }

    private static class HasSystemOutVisitor extends JavaIsoVisitor<Boolean> {
        private static final MethodMatcher PRINT_STACK_TRACE = new MethodMatcher("java.lang.Throwable printStackTrace(..)");
        private boolean foundSystemOut = false;

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Boolean ctx) {
            if (method.getSelect() != null) {
                String selectStr = method.getSelect().toString();
                if (selectStr.equals("System.out") || selectStr.equals("System.err")) {
                    foundSystemOut = true;
                }
            }

            if (PRINT_STACK_TRACE.matches(method)) {
                foundSystemOut = true;
            }

            return super.visitMethodInvocation(method, ctx);
        }

        public boolean hasSystemOut() {
            return foundSystemOut;
        }
    }
}
