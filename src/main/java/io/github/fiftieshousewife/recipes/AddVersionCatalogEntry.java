package io.github.fiftieshousewife.recipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Adds an entry to a Gradle version catalog ({@code gradle/libs.versions.toml}).
 * Adds a value to the {@code [versions]} table and a module binding to the
 * {@code [libraries]} table. If an alias is already present, does nothing.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class AddVersionCatalogEntry extends Recipe {

    private static final String VERSION_ROW_TEMPLATE = "%s = \"%s\"";
    private static final String LIBRARY_ROW_TEMPLATE = "%s = { module = \"%s\", version.ref = \"%s\" }";

    @Option(displayName = "Version alias",
            description = "Alias used as the key in the [versions] table.",
            example = "lombok")
    String versionAlias;

    @Option(displayName = "Version value",
            description = "Version string.",
            example = "1.18.44")
    String versionValue;

    @Option(displayName = "Library alias",
            description = "Alias used as the key in the [libraries] table.",
            example = "lombok")
    String libraryAlias;

    @Option(displayName = "Module coordinates",
            description = "Module in group:artifact form.",
            example = "org.projectlombok:lombok")
    String module;

    @Override
    public String getDisplayName() {
        return "Add entry to Gradle version catalog";
    }

    @Override
    public String getDescription() {
        return "Adds a [versions] and [libraries] entry to gradle/libs.versions.toml.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("gradle", "catalog");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
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
        List<TomlValue> values = document.getValues();
        values = addIfMissing(values, CatalogTable.VERSIONS, versionAlias,
                VERSION_ROW_TEMPLATE.formatted(versionAlias, versionValue));
        values = addIfMissing(values, CatalogTable.LIBRARIES, libraryAlias,
                LIBRARY_ROW_TEMPLATE.formatted(libraryAlias, module, versionAlias));
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
