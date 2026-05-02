package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Rewrites inline Gradle dependency string literals ({@code "group:artifact:version"})
 * into version-catalog references ({@code libs.alias}) inside a Gradle Kotlin DSL
 * dependencies block — but only when the project actually has a version catalog.
 * Match is by module coordinates (group:artifact); the version segment of the literal
 * is ignored.
 *
 * <p>Two-phase (scanning) recipe:
 * <ol>
 *   <li>Scan: is there a {@code gradle/libs.versions.toml} in the source set?</li>
 *   <li>Visit: if yes, rewrite matching inline declarations; if no, leave the project
 *       alone so this recipe is safe to compose unconditionally.</li>
 * </ol>
 *
 * <p>Intended to run after {@code org.openrewrite.gradle.AddDependency} in a
 * catalog-aware composition: AddDependency inserts the inline line, this recipe
 * converts it to the catalog reference when a catalog exists.
 */
@NullMarked
public final class UseCatalogReferenceForDependency
        extends ScanningRecipe<UseCatalogReferenceForDependency.Accumulator> {

    @Option(displayName = "Module coordinates",
            description = "The dependency module in group:artifact form.",
            example = "org.projectlombok:lombok")
    private final String module;

    @Option(displayName = "Catalog alias path",
            description = "The Gradle-accessor form of the catalog alias (e.g. libs.lombok or libs.log4jApi).",
            example = "libs.lombok")
    private final String catalogReference;

    public UseCatalogReferenceForDependency(final String module, final String catalogReference) {
        this.module = module;
        this.catalogReference = catalogReference;
    }

    @SuppressWarnings("unused") public String getModule() { return module; }
    @SuppressWarnings("unused") public String getCatalogReference() { return catalogReference; }

    @Override
    public String getDisplayName() {
        return "Replace an inline dependency string with a version-catalog reference";
    }

    @Override
    public String getDescription() {
        return "Rewrites inline `configuration(\"group:artifact:version\")` calls into "
                + "`configuration(libs.alias)` references inside a Gradle dependencies block. "
                + "Only applies when the project has a gradle/libs.versions.toml catalog.";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof UseCatalogReferenceForDependency other)) return false;
        return module.equals(other.module) && catalogReference.equals(other.catalogReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, catalogReference);
    }

    @Override
    public Accumulator getInitialValue(final ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(final Accumulator acc) {
        return new TreeVisitor<>() {
            @Override
            public Tree preVisit(final Tree tree, final ExecutionContext ctx) {
                if (tree instanceof SourceFile sourceFile
                        && sourceFile.getSourcePath().toString().endsWith("libs.versions.toml")) {
                    acc.catalogFound = true;
                }
                stopAfterPreVisit();
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(final Accumulator acc) {
        if (!acc.catalogFound) {
            return TreeVisitor.noop();
        }
        return new JavaIsoVisitor<>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(final J.MethodInvocation method,
                                                            final ExecutionContext ctx) {
                final J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                final List<Expression> args = visited.getArguments();
                if (args.size() != 1) {
                    return visited;
                }
                if (!(args.get(0) instanceof J.Literal literal)) {
                    return visited;
                }
                if (!(literal.getValue() instanceof String coordinates)) {
                    return visited;
                }
                if (!matchesModule(coordinates, module)) {
                    return visited;
                }
                final Expression replacement = buildCatalogReference(catalogReference, literal.getPrefix())
                        .withMarkers(literal.getMarkers());
                final JContainer<Expression> newArgs = JContainer.withElements(
                        visited.getPadding().getArguments(), List.of(replacement));
                return visited.getPadding().withArguments(newArgs);
            }
        };
    }

    static Expression buildCatalogReference(final String reference, final Space prefix) {
        final String[] parts = reference.split("\\.");
        Expression expr = identifier(parts[0], prefix);
        for (int i = 1; i < parts.length; i++) {
            expr = new J.FieldAccess(
                    Tree.randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    expr,
                    JLeftPadded.build(identifier(parts[i], Space.EMPTY)),
                    null);
        }
        return expr;
    }

    private static J.Identifier identifier(final String name, final Space prefix) {
        return new J.Identifier(
                Tree.randomId(),
                prefix,
                Markers.EMPTY,
                Collections.emptyList(),
                name,
                null,
                null);
    }

    static boolean matchesModule(final String literal, final String module) {
        return literal.equals(module)
                || literal.startsWith(module + ":")
                || literal.startsWith(module + "@");
    }

    public static final class Accumulator {
        boolean catalogFound;
    }
}
