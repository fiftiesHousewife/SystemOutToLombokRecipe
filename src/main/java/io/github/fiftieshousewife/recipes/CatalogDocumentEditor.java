package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.toml.TomlParser;
import org.openrewrite.toml.tree.Space;
import org.openrewrite.toml.tree.Toml;
import org.openrewrite.toml.tree.TomlValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure helpers for editing a Gradle version-catalog document in place. Keeps
 * {@link AddVersionCatalogEntry} focused on the recipe contract; this class
 * holds the TOML-shape mechanics and is tested directly by
 * {@code AddVersionCatalogEntryMethodTest}.
 */
@NullMarked
final class CatalogDocumentEditor {

    private static final String VERSION_ROW_TEMPLATE = "%s = \"%s\"";
    private static final String LIBRARY_ROW_TEMPLATE = "%s = { module = \"%s\", version.ref = \"%s\" }";

    private CatalogDocumentEditor() {
    }

    static Toml.Document updateDocument(final Toml.Document document, final CatalogEntry entry) {
        List<TomlValue> values = document.getValues();
        values = addIfMissing(values, CatalogTable.VERSIONS, entry.versionAlias(),
                VERSION_ROW_TEMPLATE.formatted(entry.versionAlias(), entry.versionValue()));
        values = addIfMissing(values, CatalogTable.LIBRARIES, entry.libraryAlias(),
                LIBRARY_ROW_TEMPLATE.formatted(entry.libraryAlias(), entry.module(), entry.versionAlias()));
        return values == document.getValues() ? document : document.withValues(values);
    }

    static List<TomlValue> addIfMissing(final List<TomlValue> values, final CatalogTable table,
                                        final String key, final String rowSource) {
        return tableHasKey(values, table, key) ? values : addRow(values, table, rowSource);
    }

    static boolean tableHasKey(final List<TomlValue> values, final CatalogTable table, final String key) {
        return values.stream()
                .filter(Toml.Table.class::isInstance)
                .map(Toml.Table.class::cast)
                .filter(t -> tableNameMatches(t, table))
                .flatMap(t -> t.getValues().stream())
                .filter(Toml.KeyValue.class::isInstance)
                .map(Toml.KeyValue.class::cast)
                .anyMatch(kv -> kv.getKey() instanceof Toml.Identifier id && id.getName().equals(key));
    }

    static boolean tableNameMatches(final Toml.Table tomlTable, final CatalogTable table) {
        final Toml.Identifier name = tomlTable.getName();
        return name != null && table.tomlName().equals(name.getName());
    }

    static List<TomlValue> addRow(final List<TomlValue> values,
                                  final CatalogTable table,
                                  final String rowSource) {
        final List<TomlValue> mapped = values.stream()
                .map(value -> appendRowIfMatchingTable(value, table, rowSource))
                .toList();
        if (containsTableNamed(values, table)) {
            return mapped;
        }
        final List<TomlValue> withNewTable = new ArrayList<>(mapped);
        withNewTable.add(parseTable("[" + table.tomlName() + "]\n" + rowSource + "\n").withPrefix(leadingNewline()));
        return withNewTable;
    }

    static boolean containsTableNamed(final List<TomlValue> values, final CatalogTable table) {
        return values.stream()
                .anyMatch(value -> value instanceof Toml.Table tomlTable && tableNameMatches(tomlTable, table));
    }

    static TomlValue appendRowIfMatchingTable(final TomlValue value,
                                              final CatalogTable table,
                                              final String rowSource) {
        if (!(value instanceof Toml.Table tomlTable) || !tableNameMatches(tomlTable, table)) {
            return value;
        }
        final List<Toml> children = new ArrayList<>(tomlTable.getValues());
        children.add(parseKeyValue(rowSource).withPrefix(leadingNewline()));
        return tomlTable.withValues(children);
    }

    enum CatalogTable {
        VERSIONS("versions"),
        LIBRARIES("libraries");

        private final String tomlName;

        CatalogTable(final String tomlName) {
            this.tomlName = tomlName;
        }

        String tomlName() {
            return tomlName;
        }
    }

    record CatalogEntry(String versionAlias, String versionValue, String libraryAlias, String module) {
    }

    static Space leadingNewline() {
        return Space.build("\n", Collections.emptyList());
    }

    static Toml.KeyValue parseKeyValue(final String source) {
        final Toml.Document doc = (Toml.Document) TomlParser.builder().build()
                .parse(source).findFirst().orElseThrow();
        return (Toml.KeyValue) doc.getValues().get(0);
    }

    static Toml.Table parseTable(final String source) {
        final Toml.Document doc = (Toml.Document) TomlParser.builder().build()
                .parse(source).findFirst().orElseThrow();
        return (Toml.Table) doc.getValues().get(0);
    }
}
