package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

@NullMarked
final class IdentifierUsageCounter extends JavaIsoVisitor<Integer> {
    private final String fieldName;
    private final J.VariableDeclarations declaringField;
    int usages;

    IdentifierUsageCounter(final String fieldName, final J.VariableDeclarations declaringField) {
        this.fieldName = fieldName;
        this.declaringField = declaringField;
    }

    @Override
    @SuppressWarnings("PMD.CompareObjectsWithEquals") // AST node identity, not value equality
    public J.VariableDeclarations visitVariableDeclarations(final J.VariableDeclarations varDecl, final Integer p) {
        if (varDecl == declaringField) {
            return varDecl;
        }
        return super.visitVariableDeclarations(varDecl, p);
    }

    @Override
    public J.Identifier visitIdentifier(final J.Identifier identifier, final Integer p) {
        if (fieldName.equals(identifier.getSimpleName())) {
            usages++;
        }
        return super.visitIdentifier(identifier, p);
    }
}
