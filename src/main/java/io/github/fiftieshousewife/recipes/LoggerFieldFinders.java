package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Optional;

/**
 * Field-finders shared by the Direct/Manual/Commons logger-to-@Slf4j recipes.
 * The shape is the same across all three: scan a class body for variable
 * declarations of a specific logger type, with optional modifier and
 * initialiser constraints, and decide whether exactly one match qualifies.
 */
@NullMarked
final class LoggerFieldFinders {

    private LoggerFieldFinders() {
    }

    /**
     * Returns the single static final, non-public/non-protected logger field
     * of the given type initialised by the given factory method, or empty if
     * zero or more than one such field exists. Used by recipes that target
     * directly-declared logger fields where ambiguity should skip the class.
     */
    static Optional<LoggerField> findExactlyOneEligibleField(
            final J.ClassDeclaration classDecl,
            final String typeFqn,
            final MethodMatcher initialiserMatcher) {
        final List<J.VariableDeclarations> matches = classDecl.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .filter(varDecl -> isOfType(varDecl, typeFqn))
                .filter(LoggerFieldFinders::isSingleVariable)
                .filter(LoggerFieldFinders::hasRequiredModifiers)
                .filter(varDecl -> isInitialisedBy(varDecl, initialiserMatcher))
                .toList();
        return matches.size() == 1 ? toLoggerField(matches.get(0)) : Optional.empty();
    }

    /**
     * Returns the first single-variable field of the given type, or empty if
     * none. Used by the Manual Log4j2 recipe where modifier/initialiser
     * checks aren't applied — a hand-rolled `Logger logger = …` field is
     * recognised regardless of how it's initialised.
     */
    static Optional<LoggerField> findFirstSingleVariableFieldOfType(
            final J.ClassDeclaration classDecl, final String typeFqn) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .filter(varDecl -> isOfType(varDecl, typeFqn))
                .filter(LoggerFieldFinders::isSingleVariable)
                .findFirst()
                .flatMap(LoggerFieldFinders::toLoggerField);
    }

    private static boolean isOfType(final J.VariableDeclarations varDecl, final String typeFqn) {
        return varDecl.getTypeExpression() != null
                && TypeUtils.isOfClassType(varDecl.getTypeExpression().getType(), typeFqn);
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

    private static boolean isInitialisedBy(final J.VariableDeclarations varDecl, final MethodMatcher matcher) {
        final Expression initializer = varDecl.getVariables().get(0).getInitializer();
        return initializer instanceof J.MethodInvocation invocation && matcher.matches(invocation);
    }

    private static Optional<LoggerField> toLoggerField(final J.VariableDeclarations varDecl) {
        return Optional.of(new LoggerField(varDecl, varDecl.getVariables().get(0).getSimpleName()));
    }
}
