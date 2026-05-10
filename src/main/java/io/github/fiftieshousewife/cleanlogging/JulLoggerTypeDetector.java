package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@NullMarked
final class JulLoggerTypeDetector extends JavaIsoVisitor<Integer> {

    private static final String JUL_PACKAGE_PREFIX = "java.util.logging.";

    final Set<String> referencedJulFqns = new HashSet<>();

    @Override
    public J.Import visitImport(final J.Import imp, final Integer p) {
        return imp;
    }

    @Override
    public J.Identifier visitIdentifier(final J.Identifier identifier, final Integer p) {
        recordIfJulType(identifier.getType());
        return super.visitIdentifier(identifier, p);
    }

    private void recordIfJulType(final @org.jspecify.annotations.Nullable JavaType type) {
        final JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq != null && fq.getFullyQualifiedName().startsWith(JUL_PACKAGE_PREFIX)) {
            referencedJulFqns.add(fq.getFullyQualifiedName());
        }
    }

    static Set<String> referencedJulFqnsIn(final J.CompilationUnit compilationUnit) {
        if (compilationUnit.getImports().stream()
                .noneMatch(imp -> imp.getTypeName() != null && imp.getTypeName().startsWith(JUL_PACKAGE_PREFIX))) {
            return Collections.emptySet();
        }
        final JulLoggerTypeDetector detector = new JulLoggerTypeDetector();
        detector.visit(compilationUnit, 0);
        return detector.referencedJulFqns;
    }
}
