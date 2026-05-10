package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.fiftieshousewife.cleanlogging.JulToSlf4j.IS_LOGGABLE;
import static io.github.fiftieshousewife.cleanlogging.LoggerNames.JUL_LOGGER;
import static io.github.fiftieshousewife.cleanlogging.LombokClasspathGate.isAvailable;

@NullMarked
class JulToSlf4jVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final String CALL_TEMPLATE = "log.%s(#{any()})";
    private static final String JUL_LOGGER_FQN = JUL_LOGGER.fqn();
    private static final String JUL_PACKAGE_PREFIX = "java.util.logging.";
    private static final String SUPPLIER_FQN = "java.util.function.Supplier";

    private final boolean requireLombokOnClasspath;

    JulToSlf4jVisitor(final boolean requireLombokOnClasspath) {
        this.requireLombokOnClasspath = requireLombokOnClasspath;
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(final J.CompilationUnit compilationUnit, final ExecutionContext ctx) {
        final J.CompilationUnit visited = super.visitCompilationUnit(compilationUnit, ctx);
        final Set<String> referenced = JulLoggerTypeDetector.referencedJulFqnsIn(visited);
        final List<J.Import> stillUsedImports = visited.getImports().stream()
                .filter(imp -> !isJulImport(imp.getTypeName()) || referenced.contains(imp.getTypeName()))
                .toList();
        return visited.withImports(stillUsedImports);
    }

    static boolean isJulImport(final @Nullable String typeName) {
        return typeName != null && typeName.startsWith(JUL_PACKAGE_PREFIX);
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
        if (requireLombokOnClasspath && !isAvailable(getCursor())) {
            return visited;
        }
        return rewriteIsLoggable(visited)
                .or(() -> rewriteLevelMethodCall(visited))
                .orElse(visited);
    }

    private Optional<J.MethodInvocation> rewriteLevelMethodCall(final J.MethodInvocation method) {
        if (method.getArguments().size() != 1) {
            return Optional.empty();
        }
        if (cannotRewriteSupplierArgument(method.getArguments().get(0))) {
            return Optional.empty();
        }
        return JulToSlf4j.julLevelOf(method)
                .map(JulLevel::slf4jMethod)
                .map(targetMethod -> rewriteAsSlf4jCall(method, targetMethod));
    }

    static boolean cannotRewriteSupplierArgument(final Expression arg) {
        if (arg instanceof J.Lambda lambda) {
            return !(lambda.getBody() instanceof Expression);
        }
        return TypeUtils.isOfClassType(arg.getType(), SUPPLIER_FQN);
    }

    private J.MethodInvocation rewriteAsSlf4jCall(final J.MethodInvocation original, final String targetMethod) {
        final Expression arg = unwrapSupplierLambda(original.getArguments().get(0));
        return JavaTemplate.builder(CALL_TEMPLATE.formatted(targetMethod))
                .build()
                .apply(getCursor(), original.getCoordinates().replace(), arg);
    }

    static Expression unwrapSupplierLambda(final Expression arg) {
        if (arg instanceof J.Lambda lambda && lambda.getBody() instanceof Expression body) {
            return body;
        }
        return arg;
    }

    private Optional<J.MethodInvocation> rewriteIsLoggable(final J.MethodInvocation method) {
        if (!IS_LOGGABLE.matches(method)) {
            return Optional.empty();
        }
        return julLevelConstantNameFrom(method.getArguments().get(0))
                .flatMap(JulLevel::byLevelName)
                .map(JulLevel::slf4jIsEnabled)
                .map(slf4jIsEnabled -> JavaTemplate.builder("log.%s()".formatted(slf4jIsEnabled))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace()));
    }

    static Optional<String> julLevelConstantNameFrom(final Expression arg) {
        if (arg instanceof J.FieldAccess fieldAccess
                && JulLevel.byLevelName(fieldAccess.getName().getSimpleName()).isPresent()) {
            return Optional.of(fieldAccess.getName().getSimpleName());
        }
        if (arg instanceof J.Identifier identifier
                && JulLevel.byLevelName(identifier.getSimpleName()).isPresent()) {
            return Optional.of(identifier.getSimpleName());
        }
        return Optional.empty();
    }

    private static Optional<J.VariableDeclarations> findJulLoggerField(final J.ClassDeclaration classDecl) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .filter(varDecl -> varDecl.getTypeExpression() != null
                        && TypeUtils.isOfClassType(varDecl.getTypeExpression().getType(), JUL_LOGGER_FQN))
                .findFirst();
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

}
