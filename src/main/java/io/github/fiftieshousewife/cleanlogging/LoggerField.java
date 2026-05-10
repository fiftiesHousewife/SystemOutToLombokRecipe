package io.github.fiftieshousewife.cleanlogging;

import org.openrewrite.java.tree.J;

record LoggerField(J.VariableDeclarations varDecl, String name) { }
