package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.Cursor;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;

import static io.github.fiftieshousewife.recipes.LombokLoggingAnnotation.SLF4J;

@NullMarked
final class LombokClasspathGate {

    private LombokClasspathGate() {
    }

    // Marker absent → treat as unavailable. Better to skip the rewrite than to
    // drop @Slf4j into a module whose classpath won't compile it.
    static boolean isAvailable(final Cursor cursor) {
        final J.CompilationUnit compilationUnit = cursor.firstEnclosing(J.CompilationUnit.class);
        if (compilationUnit == null) {
            return false;
        }
        return compilationUnit.getMarkers().findFirst(JavaSourceSet.class)
                .map(sourceSet -> sourceSet.getClasspath().stream()
                        .anyMatch(type -> SLF4J.fqn().equals(type.getFullyQualifiedName())))
                .orElse(false);
    }
}
