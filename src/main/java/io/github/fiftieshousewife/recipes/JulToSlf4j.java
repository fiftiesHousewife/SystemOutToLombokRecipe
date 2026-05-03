package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.github.fiftieshousewife.recipes.LoggerNames.JUL_LOGGER;
import static io.github.fiftieshousewife.recipes.LombokClasspathGate.isAvailable;

/**
 * Converts {@code java.util.logging.Logger} level-named method calls to the
 * {@code log.xxx(...)} methods exposed by Lombok's {@code @Slf4j} annotation,
 * and cleans up the now-dead JUL plumbing. Assumes the class has already been
 * annotated with {@code @Slf4j} — run {@link AddLombokSlf4jAnnotation} first.
 *
 * <p>Level mappings:
 * <pre>
 *   logger.severe(msg)  → log.error(msg)
 *   logger.warning(msg) → log.warn(msg)
 *   logger.info(msg)    → log.info(msg)
 *   logger.config(msg)  → log.debug(msg)
 *   logger.fine(msg)    → log.debug(msg)
 *   logger.finer(msg)   → log.trace(msg)
 *   logger.finest(msg)  → log.trace(msg)
 * </pre>
 *
 * <p>After the call conversion, if the class had a
 * {@code private static final Logger logger = Logger.getLogger(...);} field
 * and nothing else in the class still references it, the field and its
 * {@code java.util.logging.Logger} import are removed.
 *
 * <p>When {@code requireLombokOnClasspath} is set, the rewrite is skipped in
 * source files whose classpath doesn't contain {@code lombok.extern.slf4j.Slf4j}.
 */
@NullMarked
public class JulToSlf4j extends Recipe {

    private static final Map<String, MethodMatcher> MATCHERS = Map.of(
            "severe", matcher("severe"),
            "warning", matcher("warning"),
            "info", matcher("info"),
            "config", matcher("config"),
            "fine", matcher("fine"),
            "finer", matcher("finer"),
            "finest", matcher("finest"));

    static final Set<String> JUL_LEVEL_METHODS = MATCHERS.keySet();

    private static final Map<String, String> JUL_TO_LOG4J = Map.of(
            "severe", "error",
            "warning", "warn",
            "info", "info",
            "config", "debug",
            "fine", "debug",
            "finer", "trace",
            "finest", "trace");

    private static MethodMatcher matcher(final String methodName) {
        return new MethodMatcher(JUL_LOGGER.fqn() + " " + methodName + "(..)");
    }

    @Option(displayName = "Require Lombok on classpath",
            description = "When true, only rewrite JUL calls in source files where " +
                    "lombok.extern.slf4j.Slf4j is resolvable on the classpath.",
            required = false)
    private final boolean requireLombokOnClasspath;

    public JulToSlf4j() {
        this(false);
    }

    public JulToSlf4j(final boolean requireLombokOnClasspath) {
        this.requireLombokOnClasspath = requireLombokOnClasspath;
    }

    @SuppressWarnings("unused")
    public boolean isRequireLombokOnClasspath() {
        return requireLombokOnClasspath;
    }

    @Override
    public String getDisplayName() {
        return "Replace java.util.logging calls with Lombok log statements";
    }

    @Override
    public String getDescription() {
        return "Converts java.util.logging.Logger level-named method calls "
                + "(severe/warning/info/config/fine/finer/finest) to the equivalent "
                + "Lombok @Slf4j log methods (error/warn/info/debug/trace), then "
                + "removes the JUL Logger field and its import when no other references remain.";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof JulToSlf4j other)) return false;
        return requireLombokOnClasspath == other.requireLombokOnClasspath;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requireLombokOnClasspath);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {

            @Override
            public J.CompilationUnit visitCompilationUnit(final J.CompilationUnit compilationUnit,
                                                          final ExecutionContext ctx) {
                final J.CompilationUnit visited = super.visitCompilationUnit(compilationUnit, ctx);
                if (!julLoggerTypeReferencedIn(visited)) {
                    return visited.withImports(visited.getImports().stream()
                            .filter(imp -> !JUL_LOGGER.fqn().equals(imp.getTypeName()))
                            .toList());
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(final J.ClassDeclaration classDecl,
                                                            final ExecutionContext ctx) {
                final J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                return findJulLoggerField(visited)
                        .filter(field -> !fieldReferenced(visited, field))
                        .map(field -> removeField(visited, field))
                        .orElse(visited);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method,
                                                            final ExecutionContext ctx) {
                final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                return targetSlf4jMethodFor(visited)
                        .map(targetMethod -> rewriteAsSlf4jCall(visited, targetMethod))
                        .orElse(visited);
            }

            private Optional<String> targetSlf4jMethodFor(final J.MethodInvocation method) {
                return Optional.of(method)
                        .filter(m -> m.getArguments().size() == 1)
                        .filter(m -> !requireLombokOnClasspath || isAvailable(getCursor()))
                        .flatMap(JulToSlf4j::julLevelOf)
                        .map(JUL_TO_LOG4J::get);
            }

            private J.MethodInvocation rewriteAsSlf4jCall(final J.MethodInvocation original,
                                                          final String targetMethod) {
                return JavaTemplate.builder("log." + targetMethod + "(#{any()})")
                        .build()
                        .apply(getCursor(), original.getCoordinates().replace(),
                                original.getArguments().get(0));
            }
        };
    }

    static Optional<String> julLevelOf(final J.MethodInvocation method) {
        return MATCHERS.entrySet().stream()
                .filter(entry -> entry.getValue().matches(method))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private static Optional<J.VariableDeclarations> findJulLoggerField(final J.ClassDeclaration classDecl) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .filter(varDecl -> isJulLoggerType(varDecl.getTypeExpression()))
                .findFirst();
    }

    private static boolean isJulLoggerType(final @Nullable TypeTree typeExpression) {
        return typeExpression != null && TypeUtils.isOfClassType(typeExpression.getType(), JUL_LOGGER.fqn());
    }

    private static boolean fieldReferenced(final J.ClassDeclaration classDecl,
                                           final J.VariableDeclarations field) {
        final String fieldName = field.getVariables().get(0).getSimpleName();
        final IdentifierUsageCounter counter = new IdentifierUsageCounter(fieldName, field);
        counter.visit(classDecl, 0);
        return counter.usages > 0;
    }

    private static J.ClassDeclaration removeField(final J.ClassDeclaration classDecl,
                                                  final J.VariableDeclarations field) {
        final List<Statement> keep = classDecl.getBody().getStatements().stream()
                .filter(statement -> statement != field)
                .toList();
        return classDecl.withBody(classDecl.getBody().withStatements(keep));
    }

    private static boolean julLoggerTypeReferencedIn(final J.CompilationUnit compilationUnit) {
        final JulLoggerTypeDetector detector = new JulLoggerTypeDetector();
        detector.visit(compilationUnit, 0);
        return detector.found;
    }

    private static final class IdentifierUsageCounter extends JavaIsoVisitor<Integer> {
        private final String fieldName;
        private final J.VariableDeclarations declaringField;
        int usages;

        IdentifierUsageCounter(final String fieldName, final J.VariableDeclarations declaringField) {
            this.fieldName = fieldName;
            this.declaringField = declaringField;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(final J.VariableDeclarations varDecl,
                                                                 final Integer p) {
            if (varDecl == declaringField) {
                return varDecl;
            }
            return super.visitVariableDeclarations(varDecl, p);
        }

        @Override
        public J.Identifier visitIdentifier(final J.Identifier identifier, final Integer p) {
            if (fieldName.equals(identifier.getSimpleName())) {
                usages++;
            }
            return super.visitIdentifier(identifier, p);
        }
    }

    private static final class JulLoggerTypeDetector extends JavaIsoVisitor<Integer> {
        boolean found;

        @Override
        public J.Import visitImport(final J.Import imp, final Integer p) {
            return imp;
        }

        @Override
        public J.Identifier visitIdentifier(final J.Identifier identifier, final Integer p) {
            if (!found && TypeUtils.isOfClassType(identifier.getType(), JUL_LOGGER.fqn())) {
                found = true;
            }
            return super.visitIdentifier(identifier, p);
        }
    }
}
