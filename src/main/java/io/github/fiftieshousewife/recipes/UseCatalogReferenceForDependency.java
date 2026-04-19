package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.Objects;

/**
 * Rewrites inline Gradle dependency string literals ({@code "group:artifact:version"})
 * into version-catalog references ({@code libs.alias}) inside a Gradle Kotlin DSL
 * dependencies block. Match is by module coordinates (group:artifact) — the version
 * segment of the literal is ignored.
 *
 * <p>Intended to run after {@code org.openrewrite.gradle.AddDependency} in a
 * catalog-aware composition: AddDependency inserts the inline line, this recipe
 * converts it to the catalog reference.
 */
@NullMarked
public final class UseCatalogReferenceForDependency extends Recipe {

    @Option(displayName = "Module coordinates",
            description = "The dependency module in group:artifact form.",
            example = "org.projectlombok:lombok")
    private final String module;

    @Option(displayName = "Catalog alias path",
            description = "The Gradle-accessor form of the catalog alias (e.g. libs.lombok or libs.log4jApi).",
            example = "libs.lombok")
    private final String catalogReference;

    public UseCatalogReferenceForDependency(String module, String catalogReference) {
        this.module = module;
        this.catalogReference = catalogReference;
    }

    public String getModule() { return module; }
    public String getCatalogReference() { return catalogReference; }

    @Override
    public String getDisplayName() {
        return "Replace an inline dependency string with a version-catalog reference";
    }

    @Override
    public String getDescription() {
        return "Rewrites inline `configuration(\"group:artifact:version\")` calls into "
                + "`configuration(libs.alias)` references inside a Gradle dependencies block.";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UseCatalogReferenceForDependency other)) return false;
        return module.equals(other.module) && catalogReference.equals(other.catalogReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, catalogReference);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                List<Expression> args = mi.getArguments();
                if (args.size() != 1) {
                    return mi;
                }
                if (!(args.get(0) instanceof J.Literal literal)) {
                    return mi;
                }
                if (!(literal.getValue() instanceof String str)) {
                    return mi;
                }
                if (!matchesModule(str, module)) {
                    return mi;
                }
                return JavaTemplate.builder("#{}(" + catalogReference + ")")
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), mi.getSimpleName());
            }
        };
    }

    static boolean matchesModule(String literal, String module) {
        return literal.equals(module)
                || literal.startsWith(module + ":")
                || literal.startsWith(module + "@");
    }
}
