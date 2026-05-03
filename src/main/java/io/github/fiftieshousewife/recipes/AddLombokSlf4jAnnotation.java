package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

import static io.github.fiftieshousewife.recipes.LombokLoggingAnnotation.SLF4J;

/**
 * Adds the Lombok {@code @Slf4j} annotation to classes that use
 * {@code System.out}, {@code printStackTrace()}, or {@code java.util.logging.Logger}.
 * Apply this recipe before the transforms that actually rewrite those calls to
 * log statements.
 *
 * <p>When {@code requireLombokOnClasspath} is set, the annotation is only added
 * in source files whose classpath actually contains {@code lombok.extern.slf4j.Slf4j}.
 * Recipes that manage their own dependency set (like {@code SystemOutToSlf4jRecipe})
 * can leave this off; the {@code *NoDeps} variants turn it on so they don't drop
 * an {@code @Slf4j} in a module that has no Lombok.
 */
@NullMarked
public class AddLombokSlf4jAnnotation extends Recipe {

    private static final MethodMatcher PRINT_STACK_TRACE = new MethodMatcher("java.lang.Throwable printStackTrace(..)");

    private static final Set<String> LOGGER_FIELD_NAMES = Set.of("log", "logger", "LOG", "LOGGER");

    @Option(displayName = "Require Lombok on classpath",
            description = "When true, only add @Slf4j in source files where " +
                    "lombok.extern.slf4j.Slf4j is resolvable on the classpath. " +
                    "Use this in setups that don't add Lombok as part of the recipe run " +
                    "(e.g. multi-module projects where Lombok lives on only some modules).",
            required = false)
    private final boolean requireLombokOnClasspath;

    public AddLombokSlf4jAnnotation() {
        this(false);
    }

    public AddLombokSlf4jAnnotation(final boolean requireLombokOnClasspath) {
        this.requireLombokOnClasspath = requireLombokOnClasspath;
    }

    @SuppressWarnings("unused")
    public boolean isRequireLombokOnClasspath() {
        return requireLombokOnClasspath;
    }

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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof AddLombokSlf4jAnnotation other)) return false;
        return requireLombokOnClasspath == other.requireLombokOnClasspath;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requireLombokOnClasspath);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AddAnnotationVisitor(requireLombokOnClasspath);
    }

    static class AddAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final boolean requireLombokOnClasspath;

        AddAnnotationVisitor() {
            this(false);
        }

        AddAnnotationVisitor(final boolean requireLombokOnClasspath) {
            this.requireLombokOnClasspath = requireLombokOnClasspath;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(final J.ClassDeclaration classDecl, final ExecutionContext ctx) {
            return needsSlf4jAnnotation(classDecl) ? addSlf4jAnnotation(classDecl) : classDecl;
        }

        private boolean needsSlf4jAnnotation(final J.ClassDeclaration classDecl) {
            final boolean hasSop = containsSystemOutCalls(classDecl);
            final boolean hasJul = containsJulCalls(classDecl);
            if (!hasSop && !hasJul) {
                return false;
            }
            if (hasLombokLoggingAnnotation(classDecl)) {
                return false;
            }
            if (hasExplicitLoggerField(classDecl) && !hasJul) {
                return false;
            }
            return !requireLombokOnClasspath || LombokClasspathGate.isAvailable(getCursor());
        }

        J.ClassDeclaration addSlf4jAnnotation(final J.ClassDeclaration classDecl) {
            maybeAddImport(SLF4J.fqn(), null, false);
            return JavaTemplate.builder("@Slf4j")
                    .imports(SLF4J.fqn())
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
        if (LombokLoggingAnnotation.matchesSimpleName(annotation.getSimpleName())) {
            return true;
        }
        if (annotation.getType() == null) {
            return false;
        }
        return LombokLoggingAnnotation.matchesTypeFragment(annotation.getType().toString());
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
            if (JulToSlf4j.JUL_LEVEL_METHODS.contains(method.getSimpleName())
                    && JulToSlf4j.julLevelOf(method).isPresent()) {
                found = true;
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}
