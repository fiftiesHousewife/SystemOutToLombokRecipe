package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

import static io.github.fiftieshousewife.cleanlogging.AddLombokSlf4jAnnotation.containsJulCalls;
import static io.github.fiftieshousewife.cleanlogging.AddLombokSlf4jAnnotation.containsSystemOutCalls;
import static io.github.fiftieshousewife.cleanlogging.AddLombokSlf4jAnnotation.hasExplicitLoggerField;
import static io.github.fiftieshousewife.cleanlogging.AddLombokSlf4jAnnotation.hasLombokLoggingAnnotation;
import static io.github.fiftieshousewife.cleanlogging.LombokClasspathGate.isAvailable;
import static io.github.fiftieshousewife.cleanlogging.LombokLoggingAnnotation.SLF4J;

@NullMarked
class AddLombokSlf4jVisitor extends JavaIsoVisitor<ExecutionContext> {

    private final boolean requireLombokOnClasspath;

    AddLombokSlf4jVisitor(final boolean requireLombokOnClasspath) {
        this.requireLombokOnClasspath = requireLombokOnClasspath;
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(final J.ClassDeclaration classDecl, final ExecutionContext ctx) {
        return needsSlf4jAnnotation(classDecl) ? addSlf4jAnnotation(classDecl) : classDecl;
    }

    private boolean needsSlf4jAnnotation(final J.ClassDeclaration classDecl) {
        return (containsJulCalls(classDecl)
                        || (containsSystemOutCalls(classDecl) && !hasExplicitLoggerField(classDecl)))
                && !hasLombokLoggingAnnotation(classDecl)
                && (!requireLombokOnClasspath || isAvailable(getCursor()));
    }

    private J.ClassDeclaration addSlf4jAnnotation(final J.ClassDeclaration classDecl) {
        maybeAddImport(SLF4J.fqn(), null, false);
        return JavaTemplate.builder("@Slf4j")
                .imports(SLF4J.fqn())
                .build()
                .apply(getCursor(),
                        classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
    }
}
