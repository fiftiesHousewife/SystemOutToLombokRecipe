package io.github.fiftieshousewife.recipes;

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
 * Adds the Lombok {@code @Slf4j} annotation to classes that use
 * {@code System.out}, {@code printStackTrace()}, or {@code java.util.logging.Logger}.
 * Apply this recipe before the transforms that actually rewrite those calls to
 * log statements.
 */
@NullMarked
public class AddLombokSlf4jAnnotation extends Recipe {

    private static final MethodMatcher PRINT_STACK_TRACE = new MethodMatcher("java.lang.Throwable printStackTrace(..)");

    private static final Set<String> JUL_LEVEL_METHODS = Set.of(
            "severe", "warning", "info", "config", "fine", "finer", "finest");

    private static final Set<String> LOMBOK_LOGGING_SIMPLE_NAMES = Set.of(
            "Slf4j", "Log4j", "Log4j2", "Log", "CommonsLog", "Flogger", "JBossLog", "CustomLog");

    private static final Set<String> LOMBOK_LOGGING_TYPE_FRAGMENTS = Set.of(
            LoggerNames.LOMBOK_SLF4J,
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
        return "Add Lombok @Slf4j annotation to classes";
    }

    @Override
    public String getDescription() {
        return "Adds the Lombok @Slf4j annotation to classes that contain System.out print statements, " +
                "enabling the use of a 'log' field for logging.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AddAnnotationVisitor();
    }

    static class AddAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(final J.ClassDeclaration classDecl, final ExecutionContext ctx) {
            final boolean hasSop = containsSystemOutCalls(classDecl);
            final boolean hasJul = containsJulCalls(classDecl);
            if (!hasSop && !hasJul) {
                return classDecl;
            }
            if (hasLombokLoggingAnnotation(classDecl)) {
                return classDecl;
            }
            if (hasExplicitLoggerField(classDecl) && !hasJul) {
                return classDecl;
            }
            return addSlf4jAnnotation(classDecl);
        }

        J.ClassDeclaration addSlf4jAnnotation(final J.ClassDeclaration classDecl) {
            maybeAddImport(LoggerNames.LOMBOK_SLF4J, null, false);
            return JavaTemplate.builder("@Slf4j")
                    .imports(LoggerNames.LOMBOK_SLF4J)
                    .build()
                    .apply(getCursor(),
                            classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
        }
    }

    static boolean containsSystemOutCalls(final J.ClassDeclaration classDecl) {
        final SystemOutDetector detector = new SystemOutDetector();
        detector.visit(classDecl, false);
        return detector.found;
    }

    static boolean containsJulCalls(final J.ClassDeclaration classDecl) {
        final JulCallDetector detector = new JulCallDetector();
        detector.visit(classDecl, false);
        return detector.found;
    }

    static boolean hasLombokLoggingAnnotation(final J.ClassDeclaration classDecl) {
        return classDecl.getLeadingAnnotations().stream().anyMatch(AddLombokSlf4jAnnotation::isLombokLoggingAnnotation);
    }

    static boolean isLombokLoggingAnnotation(final J.Annotation annotation) {
        if (LOMBOK_LOGGING_SIMPLE_NAMES.contains(annotation.getSimpleName())) {
            return true;
        }
        if (annotation.getType() == null) {
            return false;
        }
        final String typeName = annotation.getType().toString();
        return LOMBOK_LOGGING_TYPE_FRAGMENTS.stream().anyMatch(typeName::contains);
    }

    static boolean hasExplicitLoggerField(final J.ClassDeclaration classDecl) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .anyMatch(AddLombokSlf4jAnnotation::isLoggerVariable);
    }

    static boolean isLoggerVariable(final J.VariableDeclarations variableDeclarations) {
        return variableDeclarations.getVariables().stream()
                .anyMatch(variable -> LOGGER_FIELD_NAMES.contains(variable.getSimpleName()));
    }

    private static final class SystemOutDetector extends JavaIsoVisitor<Boolean> {
        boolean found;

        @Override
        public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final Boolean ctx) {
            if (SystemOutToSlf4j.isSystemOutOrErr(method) || PRINT_STACK_TRACE.matches(method)) {
                found = true;
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }

    private static final class JulCallDetector extends JavaIsoVisitor<Boolean> {
        boolean found;

        @Override
        public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final Boolean ctx) {
            if (JUL_LEVEL_METHODS.contains(method.getSimpleName())
                    && JulToSlf4j.julLevelOf(method).isPresent()) {
                found = true;
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}
