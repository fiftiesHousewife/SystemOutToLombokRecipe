package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

import java.util.Comparator;
import java.util.List;

import static io.github.fiftieshousewife.recipes.ConvertManualLoggerToSlf4j.findManualLog4j2Field;
import static io.github.fiftieshousewife.recipes.ConvertManualLoggerToSlf4j.removeField;
import static io.github.fiftieshousewife.recipes.ConvertManualLoggerToSlf4j.renameReferences;
import static io.github.fiftieshousewife.recipes.LombokClasspathGate.isAvailable;
import static io.github.fiftieshousewife.recipes.LombokLoggingAnnotation.SLF4J;
import static io.github.fiftieshousewife.recipes.RemoveUnusedLoggerImports.stillUsedImports;

@NullMarked
class ConvertManualLoggerToSlf4jVisitor extends JavaIsoVisitor<ExecutionContext> {

    private final boolean requireLombokOnClasspath;

    ConvertManualLoggerToSlf4jVisitor(final boolean requireLombokOnClasspath) {
        this.requireLombokOnClasspath = requireLombokOnClasspath;
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
                && findManualLog4j2Field(classDecl).isPresent()
                && (!requireLombokOnClasspath || isAvailable(getCursor()));
    }

    private J.ClassDeclaration convertToSlf4j(final J.ClassDeclaration classDecl) {
        maybeAddImport(SLF4J.fqn(), null, false);
        final J.ClassDeclaration annotated = JavaTemplate.builder("@Slf4j")
                .imports(SLF4J.fqn())
                .build()
                .apply(getCursor(),
                        classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

        return findManualLog4j2Field(annotated)
                .map(field -> removeField(renameReferences(annotated, field.name()), field.varDecl()))
                .orElse(annotated);
    }
}
