package com.yourorg.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

/**
 * Adds the Lombok @Log4j2 annotation to classes that use System.out print statements.
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
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                if (!containsSystemOutCalls(classDecl)) {
                    return cd;
                }

                if (hasLombokLoggingAnnotation(cd)) {
                    return cd;
                }

                if (hasExplicitLoggerField(cd)) {
                    return cd;
                }

                return addLog4j2Annotation(cd);
            }

            private boolean containsSystemOutCalls(J.ClassDeclaration classDecl) {
                HasSystemOutVisitor visitor = new HasSystemOutVisitor();
                visitor.visit(classDecl, false);
                return visitor.hasSystemOut();
            }

            private boolean hasLombokLoggingAnnotation(J.ClassDeclaration classDecl) {
                return classDecl.getLeadingAnnotations().stream()
                        .anyMatch(this::isLombokLoggingAnnotation);
            }

            private boolean isLombokLoggingAnnotation(J.Annotation annotation) {
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

            private boolean hasExplicitLoggerField(J.ClassDeclaration classDecl) {
                return classDecl.getBody().getStatements().stream()
                        .filter(J.VariableDeclarations.class::isInstance)
                        .map(J.VariableDeclarations.class::cast)
                        .anyMatch(this::isLoggerVariable);
            }

            private boolean isLoggerVariable(J.VariableDeclarations variableDeclarations) {
                return variableDeclarations.getVariables().stream()
                        .anyMatch(var -> "log".equals(var.getSimpleName())
                                || "logger".equals(var.getSimpleName())
                                || "LOG".equals(var.getSimpleName())
                                || "LOGGER".equals(var.getSimpleName()));
            }

            private J.ClassDeclaration addLog4j2Annotation(J.ClassDeclaration classDecl) {
                maybeAddImport("lombok.extern.log4j.Log4j2");

                return JavaTemplate.builder("@Log4j2")
                        .javaParser(JavaParser.fromJavaVersion().classpath("lombok"))
                        .imports("lombok.extern.log4j.Log4j2")
                        .build()
                        .apply(
                                getCursor(),
                                classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                        );
            }
        };
    }

    /**
     * Helper visitor to detect if a class contains System.out method calls
     */
    private static class HasSystemOutVisitor extends JavaIsoVisitor<Boolean> {
        private boolean foundSystemOut = false;

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Boolean ctx) {
            if (method.getSelect() != null) {
                String selectStr = method.getSelect().toString();
                if (selectStr.equals("System.out") || selectStr.equals("System.err")) {
                    foundSystemOut = true;
                }
            }
            return super.visitMethodInvocation(method, ctx);
        }

        public boolean hasSystemOut() {
            return foundSystemOut;
        }
    }
}
