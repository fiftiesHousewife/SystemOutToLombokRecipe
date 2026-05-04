package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Optional;

import static io.github.fiftieshousewife.recipes.JulToSlf4j.JUL_TO_LOG4J;
import static io.github.fiftieshousewife.recipes.LoggerNames.JUL_LOGGER;
import static io.github.fiftieshousewife.recipes.LombokClasspathGate.isAvailable;

@NullMarked
class JulToSlf4jVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final String CALL_TEMPLATE = "log.%s(#{any()})";
    private static final String JUL_LOGGER_FQN = JUL_LOGGER.fqn();

    private final boolean requireLombokOnClasspath;

    JulToSlf4jVisitor(final boolean requireLombokOnClasspath) {
        this.requireLombokOnClasspath = requireLombokOnClasspath;
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(final J.CompilationUnit compilationUnit, final ExecutionContext ctx) {
        final J.CompilationUnit visited = super.visitCompilationUnit(compilationUnit, ctx);
        final boolean julLoggerImportUnused = !julLoggerTypeReferencedIn(visited);
        return julLoggerImportUnused ? withoutJulLoggerImport(visited) : visited;
    }

    private static J.CompilationUnit withoutJulLoggerImport(final J.CompilationUnit cu) {
        return cu.withImports(cu.getImports().stream()
                .filter(imp -> !isJulLoggerFqn(imp.getTypeName()))
                .toList());
    }

    static boolean isJulLoggerFqn(final @Nullable String typeName) {
        return JUL_LOGGER_FQN.equals(typeName);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(final J.ClassDeclaration classDecl, final ExecutionContext ctx) {
        final J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
        return findJulLoggerField(visited)
                .filter(field -> !fieldReferenced(visited, field))
                .map(field -> removeField(visited, field))
                .orElse(visited);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final ExecutionContext ctx) {
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

    private J.MethodInvocation rewriteAsSlf4jCall(final J.MethodInvocation original, final String targetMethod) {
        return JavaTemplate.builder(CALL_TEMPLATE.formatted(targetMethod))
                .build()
                .apply(getCursor(), original.getCoordinates().replace(), original.getArguments().get(0));
    }

    private static Optional<J.VariableDeclarations> findJulLoggerField(final J.ClassDeclaration classDecl) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .filter(varDecl -> isJulLoggerType(varDecl.getTypeExpression()))
                .findFirst();
    }

    private static boolean isJulLoggerType(final @Nullable TypeTree typeExpression) {
        return typeExpression != null && TypeUtils.isOfClassType(typeExpression.getType(), JUL_LOGGER_FQN);
    }

    private static boolean fieldReferenced(final J.ClassDeclaration classDecl, final J.VariableDeclarations field) {
        final String fieldName = field.getVariables().get(0).getSimpleName();
        final IdentifierUsageCounter counter = new IdentifierUsageCounter(fieldName, field);
        counter.visit(classDecl, 0);
        return counter.usages > 0;
    }

    private static J.ClassDeclaration removeField(final J.ClassDeclaration classDecl, final J.VariableDeclarations field) {
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

    private static final class JulLoggerTypeDetector extends JavaIsoVisitor<Integer> {
        boolean found;

        @Override
        public J.Import visitImport(final J.Import imp, final Integer p) {
            return imp;
        }

        @Override
        public J.Identifier visitIdentifier(final J.Identifier identifier, final Integer p) {
            if (!found && TypeUtils.isOfClassType(identifier.getType(), JUL_LOGGER_FQN)) {
                found = true;
            }
            return super.visitIdentifier(identifier, p);
        }
    }
}
