package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.java.tree.J;

import java.util.Optional;

@NullMarked
class DirectSlf4jLoggerFieldToLombokVisitor extends LoggerFieldToSlf4jBaseVisitor {

    DirectSlf4jLoggerFieldToLombokVisitor(final boolean requireLombokOnClasspath) {
        super(requireLombokOnClasspath);
    }

    @Override
    Optional<LoggerField> findField(final J.ClassDeclaration classDecl) {
        return DirectSlf4jLoggerFieldToLombok.findDirectSlf4jField(classDecl);
    }
}
