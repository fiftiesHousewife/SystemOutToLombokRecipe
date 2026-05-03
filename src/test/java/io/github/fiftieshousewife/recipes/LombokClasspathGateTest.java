package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LombokClasspathGateTest {

    private final JavaParser javaParser = JavaParser.fromJavaVersion().build();
    private final ExecutionContext ctx = new InMemoryExecutionContext();

    @Test
    void returnsTrue_whenSourceSetContainsLombokSlf4j() {
        final J.CompilationUnit cu = parse().withMarkers(
                markersWithSourceSet(JavaType.ShallowClass.build(LombokLoggingAnnotation.SLF4J.fqn())));

        assertThat(LombokClasspathGate.isAvailable(cursorAt(cu))).isTrue();
    }

    @Test
    void returnsFalse_whenSourceSetLacksLombokSlf4j() {
        final J.CompilationUnit cu = parse().withMarkers(
                markersWithSourceSet(JavaType.ShallowClass.build("java.util.List")));

        assertThat(LombokClasspathGate.isAvailable(cursorAt(cu))).isFalse();
    }

    @Test
    void returnsFalse_whenSourceSetMarkerIsAbsent() {
        final J.CompilationUnit cu = parse();
        final J.CompilationUnit withoutSourceSet = cu.withMarkers(
                cu.getMarkers().removeByType(JavaSourceSet.class));

        assertThat(LombokClasspathGate.isAvailable(cursorAt(withoutSourceSet))).isFalse();
    }

    @Test
    void returnsFalse_whenCursorIsNotInsideCompilationUnit() {
        final Cursor detached = new Cursor(null, Cursor.ROOT_VALUE);

        assertThat(LombokClasspathGate.isAvailable(detached)).isFalse();
    }

    private J.CompilationUnit parse() {
        return (J.CompilationUnit) javaParser.parse("""
                package com.example;
                public class Empty {}
                """).findFirst().orElseThrow();
    }

    private org.openrewrite.marker.Markers markersWithSourceSet(JavaType.FullyQualified... classpath) {
        final JavaSourceSet sourceSet = new JavaSourceSet(
                Tree.randomId(), "main", List.of(classpath), Collections.emptyMap());
        return org.openrewrite.marker.Markers.build(List.of(sourceSet));
    }

    private Cursor cursorAt(J.CompilationUnit cu) {
        return new Cursor(new Cursor(null, Cursor.ROOT_VALUE), cu);
    }
}
