package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Converts classes that hand-roll a Log4j2 logger field into the Lombok
 * {@code @Log4j2} form. For a class like
 * <pre>
 *   public class Foo {
 *       private static final Logger logger = LogManager.getLogger(Foo.class);
 *       void m() { logger.info("hi"); }
 *   }
 * </pre>
 * produces
 * <pre>
 *   &#064;Log4j2
 *   public class Foo {
 *       void m() { log.info("hi"); }
 *   }
 * </pre>
 * Renames usages of the old field to {@code log} to match the Lombok-generated
 * field. Removes the {@code org.apache.logging.log4j.Logger} and
 * {@code LogManager} imports when they are no longer used.
 */
@NullMarked
public class ConvertManualLog4j2ToLombok extends Recipe {

    @Override
    public String getDisplayName() {
        return "Convert manually declared Log4j2 logger fields to Lombok @Log4j2";
    }

    @Override
    public String getDescription() {
        return "Finds classes that declare a `private static final Logger` field initialised from "
                + "`LogManager.getLogger(...)`, replaces them with Lombok's `@Log4j2` annotation, "
                + "and renames references to the old field so they use the Lombok-generated `log`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(final J.CompilationUnit compilationUnit,
                                                          final ExecutionContext ctx) {
                final J.CompilationUnit visited = super.visitCompilationUnit(compilationUnit, ctx);
                final List<J.Import> keep = RemoveUnusedLog4j2Imports.filter(visited);
                return keep.size() == visited.getImports().size() ? visited : visited.withImports(keep);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(final J.ClassDeclaration classDecl,
                                                            final ExecutionContext ctx) {
                final J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                if (AddLombokLog4j2Annotation.hasLombokLoggingAnnotation(visited)) {
                    return visited;
                }
                if (findManualLog4j2Field(visited).isEmpty()) {
                    return visited;
                }

                maybeAddImport(Log4j2Names.LOMBOK_LOG4J2, null, false);
                final J.ClassDeclaration annotated = JavaTemplate.builder("@Log4j2")
                        .imports(Log4j2Names.LOMBOK_LOG4J2)
                        .build()
                        .apply(getCursor(),
                                visited.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

                return findManualLog4j2Field(annotated)
                        .map(field -> removeField(renameReferences(annotated, field.name), field.varDecl))
                        .orElse(annotated);
            }
        };
    }

    static Optional<ManualField> findManualLog4j2Field(final J.ClassDeclaration classDecl) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .filter(varDecl -> isLog4j2LoggerType(varDecl.getTypeExpression()))
                .filter(varDecl -> varDecl.getVariables().size() == 1)
                .findFirst()
                .map(varDecl -> new ManualField(varDecl, varDecl.getVariables().get(0).getSimpleName()));
    }

    private static boolean isLog4j2LoggerType(final @Nullable TypeTree typeExpression) {
        if (typeExpression == null) {
            return false;
        }
        final JavaType type = typeExpression.getType();
        return TypeUtils.isOfClassType(type, Log4j2Names.LOG4J2_LOGGER);
    }

    private static J.ClassDeclaration renameReferences(final J.ClassDeclaration classDecl, final String oldName) {
        if ("log".equals(oldName)) {
            return classDecl;
        }
        return (J.ClassDeclaration) new RenameFieldReferenceVisitor(oldName).visitNonNull(classDecl, 0);
    }

    private static J.ClassDeclaration removeField(final J.ClassDeclaration classDecl,
                                                  final J.VariableDeclarations toRemove) {
        final List<Statement> keep = classDecl.getBody().getStatements().stream()
                .filter(statement -> statement != toRemove)
                .toList();
        return classDecl.withBody(classDecl.getBody().withStatements(keep));
    }

    static final class ManualField {
        final J.VariableDeclarations varDecl;
        final String name;

        ManualField(final J.VariableDeclarations varDecl, final String name) {
            this.varDecl = varDecl;
            this.name = name;
        }
    }

    private static final class RenameFieldReferenceVisitor extends JavaIsoVisitor<Integer> {
        private final String oldName;

        RenameFieldReferenceVisitor(final String oldName) {
            this.oldName = oldName;
        }

        @Override
        public J.Identifier visitIdentifier(final J.Identifier identifier, final Integer p) {
            final J.Identifier visited = super.visitIdentifier(identifier, p);
            if (!oldName.equals(visited.getSimpleName())) {
                return visited;
            }
            final Tree parent = getCursor().getParentTreeCursor().getValue();
            if (parent instanceof J.VariableDeclarations.NamedVariable) {
                return visited;
            }
            return visited.withSimpleName("log");
        }
    }
}
