package io.github.fiftieshousewife.recipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Comparator;
import java.util.Set;

import static io.github.fiftieshousewife.recipes.JulToSlf4j.JUL_LEVEL_METHODS;
import static io.github.fiftieshousewife.recipes.JulToSlf4j.julLevelOf;
import static io.github.fiftieshousewife.recipes.LombokClasspathGate.isAvailable;
import static io.github.fiftieshousewife.recipes.LombokLoggingAnnotation.SLF4J;
import static io.github.fiftieshousewife.recipes.LombokLoggingAnnotation.matchesSimpleName;
import static io.github.fiftieshousewife.recipes.LombokLoggingAnnotation.matchesTypeFragment;
import static io.github.fiftieshousewife.recipes.SystemOutToSlf4j.isSystemOutOrErr;

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
@Value
@EqualsAndHashCode(callSuper = false)
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
    boolean requireLombokOnClasspath;

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
    public Set<String> getTags() {
        return Set.of("logging", "lombok", "slf4j");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>("java.io.PrintStream println(..)"),
                        new UsesMethod<>("java.io.PrintStream print(..)"),
                        new UsesMethod<>("java.io.PrintStream printf(..)"),
                        new UsesMethod<>("java.lang.Throwable printStackTrace(..)"),
                        new UsesType<>(LoggerNames.JUL_LOGGER.fqn(), false)),
                new AddAnnotationVisitor(requireLombokOnClasspath));
    }

    static class AddAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final boolean requireLombokOnClasspath;

        AddAnnotationVisitor(final boolean requireLombokOnClasspath) {
            this.requireLombokOnClasspath = requireLombokOnClasspath;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(final J.ClassDeclaration classDecl, final ExecutionContext ctx) {
            return needsSlf4jAnnotation(classDecl) ? addSlf4jAnnotation(classDecl) : classDecl;
        }

        private boolean needsSlf4jAnnotation(final J.ClassDeclaration classDecl) {
            return (containsJulCalls(classDecl)
                            || (containsSystemOutCalls(classDecl) && !hasExplicitLoggerField(classDecl)))
                    && !hasLombokLoggingAnnotation(classDecl)
                    && (!requireLombokOnClasspath || isAvailable(getCursor()));
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
        return matchesSimpleName(annotation.getSimpleName())
                || (annotation.getType() != null && matchesTypeFragment(annotation.getType().toString()));
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
            if (isSystemOutOrErr(method) || PRINT_STACK_TRACE.matches(method)) {
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
                    && julLevelOf(method).isPresent()) {
                found = true;
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}
