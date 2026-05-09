package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.java.tree.J;

import java.util.Optional;

@NullMarked
class ConvertManualLoggerToSlf4jVisitor extends LoggerFieldToSlf4jBaseVisitor {

    ConvertManualLoggerToSlf4jVisitor(final boolean requireLombokOnClasspath) {
        super(requireLombokOnClasspath);
    }

    @Override
    Optional<LoggerField> findField(final J.ClassDeclaration classDecl) {
        return ConvertManualLoggerToSlf4j.findManualLog4j2Field(classDecl);
    }
}
