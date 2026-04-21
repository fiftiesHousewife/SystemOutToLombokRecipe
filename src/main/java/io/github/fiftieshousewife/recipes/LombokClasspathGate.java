package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.Cursor;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;

/**
 * Classpath-scoped check for whether Lombok's {@code @Slf4j} is resolvable from
 * the current source file. Used by recipes that would otherwise add {@code @Slf4j}
 * blindly in modules that don't actually have Lombok on their classpath.
 *
 * <p>Returns {@code true} only when a {@link JavaSourceSet} marker is attached
 * to the compilation unit and its classpath contains {@link LoggerNames#LOMBOK_SLF4J}.
 * If the marker is absent we cannot verify, so we treat that as unavailable —
 * better to skip than to drop an @Slf4j that won't compile.
 */
@NullMarked
final class LombokClasspathGate {

    private LombokClasspathGate() {
    }

    static boolean isAvailable(final Cursor cursor) {
        final J.CompilationUnit cu = cursor.firstEnclosing(J.CompilationUnit.class);
        if (cu == null) {
            return false;
        }
        return cu.getMarkers().findFirst(JavaSourceSet.class)
                .map(sourceSet -> sourceSet.getClasspath().stream()
                        .anyMatch(type -> LoggerNames.LOMBOK_SLF4J.equals(type.getFullyQualifiedName())))
                .orElse(false);
    }
}
