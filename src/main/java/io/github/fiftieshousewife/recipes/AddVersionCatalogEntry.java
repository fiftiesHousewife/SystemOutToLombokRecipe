package io.github.fiftieshousewife.recipes;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.toml.TomlIsoVisitor;
import org.openrewrite.toml.TomlParser;
import org.openrewrite.toml.tree.Space;
import org.openrewrite.toml.tree.Toml;
import org.openrewrite.toml.tree.TomlValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Adds an entry to a Gradle version catalog ({@code gradle/libs.versions.toml}).
 * Adds a value to the {@code [versions]} table and a module binding to the
 * {@code [libraries]} table. If an alias is already present, does nothing.
 */
@NullMarked
public final class AddVersionCatalogEntry extends Recipe {

    @Option(displayName = "Version alias",
            description = "Alias used as the key in the [versions] table.",
            example = "lombok")
    private final String versionAlias;

    @Option(displayName = "Version value",
            description = "Version string.",
            example = "1.18.44")
    private final String versionValue;

    @Option(displayName = "Library alias",
            description = "Alias used as the key in the [libraries] table.",
            example = "lombok")
    private final String libraryAlias;

    @Option(displayName = "Module coordinates",
            description = "Module in group:artifact form.",
            example = "org.projectlombok:lombok")
    private final String module;

    public AddVersionCatalogEntry(final String versionAlias,
                                  final String versionValue,
                                  final String libraryAlias,
                                  final String module) {
        this.versionAlias = versionAlias;
        this.versionValue = versionValue;
        this.libraryAlias = libraryAlias;
        this.module = module;
    }

    @SuppressWarnings("unused") public String getVersionAlias() { return versionAlias; }
    @SuppressWarnings("unused") public String getVersionValue() { return versionValue; }
    @SuppressWarnings("unused") public String getLibraryAlias() { return libraryAlias; }
    @SuppressWarnings("unused") public String getModule() { return module; }

    @Override
    public String getDisplayName() {
        return "Add entry to Gradle version catalog";
    }

    @Override
    public String getDescription() {
        return "Adds a [versions] and [libraries] entry to gradle/libs.versions.toml.";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof AddVersionCatalogEntry other)) return false;
        return versionAlias.equals(other.versionAlias)
                && versionValue.equals(other.versionValue)
                && libraryAlias.equals(other.libraryAlias)
                && module.equals(other.module);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionAlias, versionValue, libraryAlias, module);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TomlIsoVisitor<>() {
            @Override
            public Toml.Document visitDocument(final Toml.Document document, final ExecutionContext ctx) {
                if (!document.getSourcePath().toString().endsWith("libs.versions.toml")) {
                    return document;
                }
                return updateDocument(document, versionAlias, versionValue, libraryAlias, module);
            }
        };
    }

    static Toml.Document updateDocument(final Toml.Document document,
                                        final String versionAlias,
                                        final String versionValue,
                                        final String libraryAlias,
                                        final String module) {
        final boolean hasVersion = tableHasKey(document, "versions", versionAlias);
        final boolean hasLibrary = tableHasKey(document, "libraries", libraryAlias);
        if (hasVersion && hasLibrary) {
            return document;
        }
        List<TomlValue> values = new ArrayList<>(document.getValues());
        if (!hasVersion) {
            values = addRow(values, "versions", versionAlias + " = \"" + versionValue + "\"");
        }
        if (!hasLibrary) {
            final String row = libraryAlias
                    + " = { module = \"" + module + "\", version.ref = \"" + versionAlias + "\" }";
            values = addRow(values, "libraries", row);
        }
        return document.withValues(values);
    }

    static boolean tableHasKey(final Toml.Document doc, final String tableName, final String key) {
        return doc.getValues().stream()
                .filter(Toml.Table.class::isInstance)
                .map(Toml.Table.class::cast)
                .filter(table -> tableNameMatches(table, tableName))
                .flatMap(table -> table.getValues().stream())
                .filter(Toml.KeyValue.class::isInstance)
                .map(Toml.KeyValue.class::cast)
                .anyMatch(kv -> kv.getKey() instanceof Toml.Identifier id && id.getName().equals(key));
    }

    private static boolean tableNameMatches(final Toml.Table table, final String name) {
        final Toml.Identifier tableName = table.getName();
        return tableName != null && name.equals(tableName.getName());
    }

    private static List<TomlValue> addRow(final List<TomlValue> values,
                                          final String tableName,
                                          final String rowSource) {
        final List<TomlValue> mapped = values.stream()
                .map(value -> appendRowIfMatchingTable(value, tableName, rowSource))
                .toList();
        if (containsTableNamed(values, tableName)) {
            return mapped;
        }
        final List<TomlValue> withNewTable = new ArrayList<>(mapped);
        withNewTable.add(parseTable("[" + tableName + "]\n" + rowSource + "\n").withPrefix(leadingNewline()));
        return withNewTable;
    }

    private static boolean containsTableNamed(final List<TomlValue> values, final String tableName) {
        return values.stream()
                .anyMatch(value -> value instanceof Toml.Table table && tableNameMatches(table, tableName));
    }

    private static TomlValue appendRowIfMatchingTable(final TomlValue value,
                                                      final String tableName,
                                                      final String rowSource) {
        if (!(value instanceof Toml.Table table) || !tableNameMatches(table, tableName)) {
            return value;
        }
        final List<Toml> children = new ArrayList<>(table.getValues());
        children.add(parseKeyValue(rowSource).withPrefix(leadingNewline()));
        return table.withValues(children);
    }

    private static Space leadingNewline() {
        return Space.build("\n", Collections.emptyList());
    }

    private static Toml.KeyValue parseKeyValue(final String source) {
        final Toml.Document doc = (Toml.Document) TomlParser.builder().build()
                .parse(source).findFirst().orElseThrow();
        return (Toml.KeyValue) doc.getValues().get(0);
    }

    private static Toml.Table parseTable(final String source) {
        final Toml.Document doc = (Toml.Document) TomlParser.builder().build()
                .parse(source).findFirst().orElseThrow();
        return (Toml.Table) doc.getValues().get(0);
    }
}
