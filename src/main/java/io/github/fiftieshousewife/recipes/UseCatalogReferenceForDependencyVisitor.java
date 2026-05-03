package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;

import java.util.List;
import java.util.Optional;

import static io.github.fiftieshousewife.recipes.UseCatalogReferenceForDependency.buildCatalogReference;
import static io.github.fiftieshousewife.recipes.UseCatalogReferenceForDependency.matchesModule;

@NullMarked
class UseCatalogReferenceForDependencyVisitor extends JavaIsoVisitor<ExecutionContext> {

    private final String module;
    private final String catalogReference;

    UseCatalogReferenceForDependencyVisitor(final String module, final String catalogReference) {
        this.module = module;
        this.catalogReference = catalogReference;
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method, final ExecutionContext ctx) {
        final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
        return matchedCoordinateLiteral(visited)
                .map(literal -> rewriteAsCatalogRef(visited, literal))
                .orElse(visited);
    }

    private Optional<J.Literal> matchedCoordinateLiteral(final J.MethodInvocation method) {
        final List<Expression> args = method.getArguments();
        if (args.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(args.get(0))
                .filter(J.Literal.class::isInstance)
                .map(J.Literal.class::cast)
                .filter(literal -> literal.getValue() instanceof String coords && matchesModule(coords, module));
    }

    private J.MethodInvocation rewriteAsCatalogRef(final J.MethodInvocation method, final J.Literal literal) {
        final Expression replacement = buildCatalogReference(catalogReference, literal.getPrefix())
                .withMarkers(literal.getMarkers());
        final JContainer<Expression> newArgs = JContainer.withElements(
                method.getPadding().getArguments(), List.of(replacement));
        return method.getPadding().withArguments(newArgs);
    }
}
