package io.github.fiftieshousewife.recipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.fiftieshousewife.recipes.LoggerNames.SLF4J_LOGGER;

/**
 * Converts a class with a directly-declared {@code static final} SLF4J
 * {@code Logger} field initialised via {@code LoggerFactory.getLogger(...)}
 * into the Lombok {@code @Slf4j} form: drops the field, adds the annotation,
 * and renames references to the old field to {@code log}. Removes the
 * {@code org.slf4j.Logger} / {@code LoggerFactory} imports once they're
 * unused.
 *
 * <p>Skips classes that already carry any Lombok logging annotation, classes
 * without exactly one eligible field, and (when
 * {@code requireLombokOnClasspath} is set) source files whose classpath
 * doesn't actually contain {@code lombok.extern.slf4j.Slf4j}.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class DirectSlf4jLoggerFieldToLombok extends Recipe {

    private static final MethodMatcher LOGGER_FACTORY_GET_LOGGER =
            new MethodMatcher("org.slf4j.LoggerFactory getLogger(..)");

    @Option(displayName = "Require Lombok on classpath",
            description = "When true, only convert direct SLF4J Logger fields in source files where " +
                    "lombok.extern.slf4j.Slf4j is resolvable on the classpath. " +
                    "Use this in setups that don't add Lombok as part of the recipe run " +
                    "(e.g. multi-module projects where Lombok lives on only some modules).",
            required = false)
    boolean requireLombokOnClasspath;

    @Override
    public String getDisplayName() {
        return "Convert directly-declared SLF4J Logger fields to Lombok @Slf4j";
    }

    @Override
    public String getDescription() {
        return "Finds classes that declare a `private` or package-private `static final Logger` field "
                + "initialised from `LoggerFactory.getLogger(...)`, replaces them with Lombok's `@Slf4j` "
                + "annotation, and renames references to the old field so they use the Lombok-generated `log`.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("logging", "lombok", "slf4j");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(3);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(SLF4J_LOGGER.fqn(), false),
                new DirectSlf4jLoggerFieldToLombokVisitor(requireLombokOnClasspath));
    }

    static Optional<DirectField> findDirectSlf4jField(final J.ClassDeclaration classDecl) {
        final List<J.VariableDeclarations> matches = classDecl.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .filter(varDecl -> isSlf4jLoggerType(varDecl.getTypeExpression()))
                .filter(DirectSlf4jLoggerFieldToLombok::isSingleVariable)
                .filter(DirectSlf4jLoggerFieldToLombok::hasRequiredModifiers)
                .filter(DirectSlf4jLoggerFieldToLombok::isLoggerFactoryInitialised)
                .toList();
        return matches.size() == 1
                ? Optional.of(new DirectField(matches.get(0), matches.get(0).getVariables().get(0).getSimpleName()))
                : Optional.empty();
    }

    private static boolean isSlf4jLoggerType(final @Nullable TypeTree typeExpression) {
        return typeExpression != null && TypeUtils.isOfClassType(typeExpression.getType(), SLF4J_LOGGER.fqn());
    }

    private static boolean isSingleVariable(final J.VariableDeclarations varDecl) {
        return varDecl.getVariables().size() == 1;
    }

    private static boolean hasRequiredModifiers(final J.VariableDeclarations varDecl) {
        boolean hasStatic = false;
        boolean hasFinal = false;
        for (final J.Modifier modifier : varDecl.getModifiers()) {
            final J.Modifier.Type type = modifier.getType();
            if (type == J.Modifier.Type.Public || type == J.Modifier.Type.Protected) {
                return false;
            }
            if (type == J.Modifier.Type.Static) {
                hasStatic = true;
            } else if (type == J.Modifier.Type.Final) {
                hasFinal = true;
            }
        }
        return hasStatic && hasFinal;
    }

    private static boolean isLoggerFactoryInitialised(final J.VariableDeclarations varDecl) {
        final Expression initializer = varDecl.getVariables().get(0).getInitializer();
        return initializer instanceof J.MethodInvocation invocation
                && LOGGER_FACTORY_GET_LOGGER.matches(invocation);
    }

    static J.ClassDeclaration renameReferences(final J.ClassDeclaration classDecl, final String oldName) {
        if ("log".equals(oldName)) {
            return classDecl;
        }
        return (J.ClassDeclaration) new LoggerFieldRenameToLogVisitor(oldName).visitNonNull(classDecl, 0);
    }

    static J.ClassDeclaration removeField(final J.ClassDeclaration classDecl, final J.VariableDeclarations toRemove) {
        final List<Statement> keep = classDecl.getBody().getStatements().stream()
                .filter(statement -> statement != toRemove)
                .toList();
        return classDecl.withBody(classDecl.getBody().withStatements(keep));
    }

    record DirectField(J.VariableDeclarations varDecl, String name) {
    }
}
