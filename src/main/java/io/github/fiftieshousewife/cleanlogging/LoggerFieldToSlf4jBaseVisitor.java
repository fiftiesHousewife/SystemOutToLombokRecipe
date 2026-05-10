package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static io.github.fiftieshousewife.cleanlogging.LombokClasspathGate.isAvailable;
import static io.github.fiftieshousewife.cleanlogging.LombokLoggingAnnotation.SLF4J;
import static io.github.fiftieshousewife.cleanlogging.RemoveUnusedLoggerImports.stillUsedImports;

/**
 * Shared lifecycle for the recipes that fold a hand-rolled logger field into
 * Lombok's {@code @Slf4j} form: prune unused logger imports at the
 * compilation-unit level, then per class, add {@code @Slf4j}, optionally apply
 * a post-template transform (e.g. method-name renames), drop the field, and
 * rename references to {@code log}. Subclasses contribute only the
 * recipe-specific field-finder and (optional) post-template hook.
 */
@NullMarked
abstract class LoggerFieldToSlf4jBaseVisitor extends JavaIsoVisitor<ExecutionContext> {

    private final boolean requireLombokOnClasspath;

    LoggerFieldToSlf4jBaseVisitor(final boolean requireLombokOnClasspath) {
        this.requireLombokOnClasspath = requireLombokOnClasspath;
    }

    abstract Optional<LoggerField> findField(J.ClassDeclaration classDecl);

    /**
     * Invoked on the class declaration immediately after {@code @Slf4j} is
     * inserted, before the field is removed and references renamed. Default
     * is the identity. Override to apply method-name renames or other
     * transforms that need to see the post-template tree.
     */
    J.ClassDeclaration postConvertHook(final J.ClassDeclaration annotated) {
        return annotated;
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(final J.CompilationUnit compilationUnit, final ExecutionContext ctx) {
        final J.CompilationUnit visited = super.visitCompilationUnit(compilationUnit, ctx);
        final List<J.Import> keep = stillUsedImports(visited);
        return keep.size() == visited.getImports().size() ? visited : visited.withImports(keep);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(final J.ClassDeclaration classDecl, final ExecutionContext ctx) {
        final J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
        return shouldConvert(visited) ? convertToSlf4j(visited) : visited;
    }

    private boolean shouldConvert(final J.ClassDeclaration classDecl) {
        return !AddLombokSlf4jAnnotation.hasLombokLoggingAnnotation(classDecl)
                && findField(classDecl).isPresent()
                && (!requireLombokOnClasspath || isAvailable(getCursor()));
    }

    private J.ClassDeclaration convertToSlf4j(final J.ClassDeclaration classDecl) {
        maybeAddImport(SLF4J.fqn(), null, false);
        final J.ClassDeclaration annotated = JavaTemplate.builder("@Slf4j")
                .imports(SLF4J.fqn())
                .build()
                .apply(getCursor(),
                        classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
        final J.ClassDeclaration postHook = postConvertHook(annotated);
        return findField(postHook)
                .map(field -> removeField(renameReferences(postHook, field.name()), field.varDecl()))
                .orElse(postHook);
    }

    static J.ClassDeclaration removeField(final J.ClassDeclaration classDecl, final J.VariableDeclarations toRemove) {
        final List<Statement> keep = classDecl.getBody().getStatements().stream()
                .filter(statement -> statement != toRemove)
                .toList();
        return classDecl.withBody(classDecl.getBody().withStatements(keep));
    }

    static J.ClassDeclaration renameReferences(final J.ClassDeclaration classDecl, final String oldName) {
        if ("log".equals(oldName)) {
            return classDecl;
        }
        return (J.ClassDeclaration) new LoggerFieldRenameToLogVisitor(oldName).visitNonNull(classDecl, 0);
    }
}
