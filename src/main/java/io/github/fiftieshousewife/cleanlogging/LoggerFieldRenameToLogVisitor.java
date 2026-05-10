package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/**
 * Renames every reference to the field named {@code oldName} to {@code log}.
 * Used by the recipes that fold a directly-declared logger field into the
 * Lombok-generated {@code log} field. Skips the declaration site itself
 * (callers remove the declaration in a separate step).
 */
@NullMarked
final class LoggerFieldRenameToLogVisitor extends JavaIsoVisitor<Integer> {

    private final String oldName;

    LoggerFieldRenameToLogVisitor(final String oldName) {
        this.oldName = oldName;
    }

    @Override
    public J.Identifier visitIdentifier(final J.Identifier identifier, final Integer p) {
        final J.Identifier visited = super.visitIdentifier(identifier, p);
        if (!oldName.equals(visited.getSimpleName())) {
            return visited;
        }
        final Tree parent = getCursor().getParentTreeCursor().getValue();
        if (parent instanceof J.VariableDeclarations.NamedVariable) {
            return visited;
        }
        return visited.withSimpleName("log");
    }
}