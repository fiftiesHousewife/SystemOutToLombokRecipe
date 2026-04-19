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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    private static final String LOG4J2_LOGGER_FQN = "org.apache.logging.log4j.Logger";
    private static final String LOG_MANAGER_FQN = "org.apache.logging.log4j.LogManager";
    private static final String LOMBOK_LOG4J2_FQN = "lombok.extern.log4j.Log4j2";

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
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit result = super.visitCompilationUnit(cu, ctx);
                String nonImportCode = result.printAll().replaceAll("(?m)^import\\s+[^;]+;\\s*$", "");
                List<J.Import> keep = new ArrayList<>(result.getImports().size());
                for (J.Import imp : result.getImports()) {
                    String fqn = imp.getTypeName();
                    String simpleName = simpleNameOf(fqn);
                    if ((LOG4J2_LOGGER_FQN.equals(fqn) || LOG_MANAGER_FQN.equals(fqn))
                            && !referencedIn(nonImportCode, simpleName)) {
                        continue;
                    }
                    keep.add(imp);
                }
                if (keep.size() != result.getImports().size()) {
                    result = result.withImports(keep);
                }
                return result;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (AddLombokLog4j2Annotation.hasLombokLoggingAnnotation(cd)) {
                    return cd;
                }
                ManualField field = findManualLog4j2Field(cd);
                if (field == null) {
                    return cd;
                }

                maybeAddImport(LOMBOK_LOG4J2_FQN, null, false);
                cd = JavaTemplate.builder("@Log4j2")
                        .imports(LOMBOK_LOG4J2_FQN)
                        .build()
                        .apply(getCursor(),
                                cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

                ManualField updatedField = findManualLog4j2Field(cd);
                if (updatedField == null) {
                    return cd;
                }
                cd = renameReferences(cd, updatedField.name);
                cd = removeField(cd, updatedField.varDecl);
                return cd;
            }
        };
    }

    private static String simpleNameOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }

    private static boolean referencedIn(String code, String simpleName) {
        return java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(simpleName) + "\\b")
                .matcher(code).find();
    }

    static @Nullable ManualField findManualLog4j2Field(J.ClassDeclaration classDecl) {
        for (Statement s : classDecl.getBody().getStatements()) {
            if (!(s instanceof J.VariableDeclarations vd)) {
                continue;
            }
            if (!isLog4j2LoggerType(vd.getTypeExpression())) {
                continue;
            }
            if (vd.getVariables().size() != 1) {
                continue;
            }
            J.VariableDeclarations.NamedVariable var = vd.getVariables().get(0);
            return new ManualField(vd, var.getSimpleName());
        }
        return null;
    }

    private static boolean isLog4j2LoggerType(@Nullable TypeTree typeExpression) {
        if (typeExpression == null) {
            return false;
        }
        JavaType type = typeExpression.getType();
        return TypeUtils.isOfClassType(type, LOG4J2_LOGGER_FQN);
    }

    private static J.ClassDeclaration renameReferences(J.ClassDeclaration cd, String oldName) {
        if ("log".equals(oldName)) {
            return cd;
        }
        return (J.ClassDeclaration) new RenameFieldReferenceVisitor(oldName).visitNonNull(cd, 0);
    }

    private static J.ClassDeclaration removeField(J.ClassDeclaration cd, J.VariableDeclarations toRemove) {
        List<Statement> keep = new ArrayList<>(cd.getBody().getStatements().size() - 1);
        for (Statement s : cd.getBody().getStatements()) {
            if (s != toRemove) {
                keep.add(s);
            }
        }
        return cd.withBody(cd.getBody().withStatements(keep));
    }

    static final class ManualField {
        final J.VariableDeclarations varDecl;
        final String name;

        ManualField(J.VariableDeclarations varDecl, String name) {
            this.varDecl = varDecl;
            this.name = name;
        }
    }

    private static final class RenameFieldReferenceVisitor extends JavaIsoVisitor<Integer> {
        private final String oldName;

        RenameFieldReferenceVisitor(String oldName) {
            this.oldName = oldName;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier ident, Integer p) {
            J.Identifier i = super.visitIdentifier(ident, p);
            if (oldName.equals(i.getSimpleName())) {
                Tree parent = getCursor().getParentTreeCursor().getValue();
                if (parent instanceof J.VariableDeclarations.NamedVariable) {
                    return i;
                }
                return i.withSimpleName("log");
            }
            return i;
        }
    }
}
