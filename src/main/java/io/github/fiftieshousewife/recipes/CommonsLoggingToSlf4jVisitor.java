package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.java.tree.J;

import java.util.Optional;

@NullMarked
class CommonsLoggingToSlf4jVisitor extends LoggerFieldToSlf4jBaseVisitor {

    CommonsLoggingToSlf4jVisitor(final boolean requireLombokOnClasspath) {
        super(requireLombokOnClasspath);
    }

    @Override
    Optional<LoggerField> findField(final J.ClassDeclaration classDecl) {
        return CommonsLoggingToSlf4j.findCommonsLogField(classDecl);
    }

    @Override
    J.ClassDeclaration postConvertHook(final J.ClassDeclaration annotated) {
        return (J.ClassDeclaration) new CommonsFatalRenameVisitor().visitNonNull(annotated, 0);
    }
}
