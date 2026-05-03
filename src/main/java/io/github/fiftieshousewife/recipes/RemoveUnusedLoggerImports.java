package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static io.github.fiftieshousewife.recipes.LoggerNames.LOG4J2_LOGGER;
import static io.github.fiftieshousewife.recipes.LoggerNames.LOG4J2_LOG_MANAGER;

/**
 * After a class body has been rewritten to no longer reference Log4j2's
 * {@code Logger} or {@code LogManager}, removes those imports from the
 * compilation unit. Returns a new import list; leaves the unit unchanged
 * if nothing to remove.
 */
@NullMarked
final class RemoveUnusedLoggerImports {

    private static final Set<String> CANDIDATES = Set.of(
            LOG4J2_LOGGER.fqn(),
            LOG4J2_LOG_MANAGER.fqn());

    private static final Pattern IMPORT_LINE = Pattern.compile("(?m)^import\\s+[^;]+;\\s*$");

    private RemoveUnusedLoggerImports() {
    }

    static List<J.Import> filter(final J.CompilationUnit compilationUnit) {
        final String nonImportCode = IMPORT_LINE.matcher(compilationUnit.printAll()).replaceAll("");
        return compilationUnit.getImports().stream()
                .filter(imp -> isStillUsed(imp.getTypeName(), nonImportCode))
                .toList();
    }

    private static boolean isStillUsed(final String fqn, final String nonImportCode) {
        return !CANDIDATES.contains(fqn) || referencedIn(nonImportCode, simpleNameOf(fqn));
    }

    private static String simpleNameOf(String fqn) {
        final int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }

    private static boolean referencedIn(String code, String simpleName) {
        return Pattern.compile("\\b" + Pattern.quote(simpleName) + "\\b")
                .matcher(code).find();
    }
}
