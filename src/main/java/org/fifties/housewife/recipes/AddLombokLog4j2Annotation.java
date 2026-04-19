package org.fifties.housewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

import java.util.Comparator;
import java.util.Set;

/**
 * Adds the Lombok {@code @Log4j2} annotation to classes that use {@code System.out}
 * or {@code printStackTrace()}. Apply this recipe before the transforms that actually
 * rewrite those calls to log statements.
 */
@NullMarked
public class AddLombokLog4j2Annotation extends Recipe {

    private static final MethodMatcher PRINT_STACK_TRACE = new MethodMatcher("java.lang.Throwable printStackTrace(..)");

    private static final Set<String> LOMBOK_LOGGING_SIMPLE_NAMES = Set.of(
            "Slf4j", "Log4j", "Log4j2", "Log", "CommonsLog", "Flogger", "JBossLog", "CustomLog");

    private static final Set<String> LOMBOK_LOGGING_TYPE_FRAGMENTS = Set.of(
            "lombok.extern.slf4j.Slf4j",
            "lombok.extern.log4j.Log4j",
            "lombok.extern.log4j.Log4j2",
            "lombok.extern.java.Log",
            "lombok.extern.apachecommons.CommonsLog",
            "lombok.extern.flogger.Flogger",
            "lombok.extern.jbosslog.JBossLog",
            "lombok.CustomLog");

    private static final Set<String> LOGGER_FIELD_NAMES = Set.of("log", "logger", "LOG", "LOGGER");

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
        return new AddAnnotationVisitor();
    }

    static class AddAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            if (!containsSystemOutCalls(classDecl)
                    || hasLombokLoggingAnnotation(classDecl)
                    || hasExplicitLoggerField(classDecl)) {
                return classDecl;
            }
            return addLog4j2Annotation(classDecl);
        }

        J.ClassDeclaration addLog4j2Annotation(J.ClassDeclaration classDecl) {
            maybeAddImport("lombok.extern.log4j.Log4j2", null, false);
            return JavaTemplate.builder("@Log4j2")
                    .imports("lombok.extern.log4j.Log4j2")
                    .build()
                    .apply(getCursor(),
                            classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
        }
    }

    static boolean containsSystemOutCalls(J.ClassDeclaration classDecl) {
        SystemOutDetector detector = new SystemOutDetector();
        detector.visit(classDecl, false);
        return detector.found;
    }

    static boolean hasLombokLoggingAnnotation(J.ClassDeclaration classDecl) {
        return classDecl.getLeadingAnnotations().stream().anyMatch(AddLombokLog4j2Annotation::isLombokLoggingAnnotation);
    }

    static boolean isLombokLoggingAnnotation(J.Annotation annotation) {
        if (LOMBOK_LOGGING_SIMPLE_NAMES.contains(annotation.getSimpleName())) {
            return true;
        }
        if (annotation.getType() == null) {
            return false;
        }
        String typeName = annotation.getType().toString();
        return LOMBOK_LOGGING_TYPE_FRAGMENTS.stream().anyMatch(typeName::contains);
    }

    static boolean hasExplicitLoggerField(J.ClassDeclaration classDecl) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .anyMatch(AddLombokLog4j2Annotation::isLoggerVariable);
    }

    static boolean isLoggerVariable(J.VariableDeclarations variableDeclarations) {
        return variableDeclarations.getVariables().stream()
                .anyMatch(v -> LOGGER_FIELD_NAMES.contains(v.getSimpleName()));
    }

    private static final class SystemOutDetector extends JavaIsoVisitor<Boolean> {
        boolean found;

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Boolean ctx) {
            if (method.getSelect() != null) {
                String select = method.getSelect().toString();
                if ("System.out".equals(select) || "System.err".equals(select)) {
                    found = true;
                }
            }
            if (PRINT_STACK_TRACE.matches(method)) {
                found = true;
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}
