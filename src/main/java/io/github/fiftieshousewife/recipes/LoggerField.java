package io.github.fiftieshousewife.recipes;

import org.openrewrite.java.tree.J;

record LoggerField(J.VariableDeclarations varDecl, String name) { }
