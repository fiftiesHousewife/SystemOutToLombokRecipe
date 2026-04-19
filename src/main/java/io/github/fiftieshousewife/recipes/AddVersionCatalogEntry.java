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

    public AddVersionCatalogEntry(String versionAlias, String versionValue, String libraryAlias, String module) {
        this.versionAlias = versionAlias;
        this.versionValue = versionValue;
        this.libraryAlias = libraryAlias;
        this.module = module;
    }

    public String getVersionAlias() { return versionAlias; }
    public String getVersionValue() { return versionValue; }
    public String getLibraryAlias() { return libraryAlias; }
    public String getModule() { return module; }

    @Override
    public String getDisplayName() {
        return "Add entry to Gradle version catalog";
    }

    @Override
    public String getDescription() {
        return "Adds a [versions] and [libraries] entry to gradle/libs.versions.toml.";
    }

    @Override
    public boolean equals(Object o) {
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
        return new TomlIsoVisitor<ExecutionContext>() {
            @Override
            public Toml.Document visitDocument(Toml.Document document, ExecutionContext ctx) {
                if (document.getSourcePath() == null
                        || !document.getSourcePath().toString().endsWith("libs.versions.toml")) {
                    return document;
                }
                return updateDocument(document, versionAlias, versionValue, libraryAlias, module);
            }
        };
    }

    static Toml.Document updateDocument(Toml.Document document, String versionAlias, String versionValue,
                                        String libraryAlias, String module) {
        boolean hasVersion = tableHasKey(document, "versions", versionAlias);
        boolean hasLibrary = tableHasKey(document, "libraries", libraryAlias);
        if (hasVersion && hasLibrary) {
            return document;
        }

        List<TomlValue> values = new ArrayList<>(document.getValues());
        if (!hasVersion) {
            values = addRow(values, "versions",
                    versionAlias + " = \"" + versionValue + "\"");
        }
        if (!hasLibrary) {
            String row = libraryAlias
                    + " = { module = \"" + module + "\", version.ref = \"" + versionAlias + "\" }";
            values = addRow(values, "libraries", row);
        }
        return document.withValues(values);
    }

    static boolean tableHasKey(Toml.Document doc, String tableName, String key) {
        for (TomlValue value : doc.getValues()) {
            if (value instanceof Toml.Table table && tableNameMatches(table, tableName)) {
                for (Toml child : table.getValues()) {
                    if (child instanceof Toml.KeyValue kv
                            && kv.getKey() instanceof Toml.Identifier id
                            && id.getName().equals(key)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean tableNameMatches(Toml.Table table, String name) {
        return name.equals(table.getName().getName());
    }

    private static List<TomlValue> addRow(List<TomlValue> values, String tableName, String rowSource) {
        List<TomlValue> result = new ArrayList<>(values.size());
        boolean tableFound = false;
        for (TomlValue value : values) {
            if (value instanceof Toml.Table table && tableNameMatches(table, tableName)) {
                tableFound = true;
                List<Toml> children = new ArrayList<>(table.getValues());
                children.add(parseKeyValue(rowSource).withPrefix(leadingNewline()));
                result.add(table.withValues(children));
            } else {
                result.add(value);
            }
        }
        if (!tableFound) {
            Toml.Table newTable = parseTable("[" + tableName + "]\n" + rowSource + "\n");
            result.add(newTable.withPrefix(leadingNewline()));
        }
        return result;
    }

    private static Space leadingNewline() {
        return Space.build("\n", Collections.emptyList());
    }

    private static Toml.KeyValue parseKeyValue(String source) {
        Toml.Document doc = (Toml.Document) TomlParser.builder().build()
                .parse(source).findFirst().orElseThrow();
        return (Toml.KeyValue) doc.getValues().get(0);
    }

    private static Toml.Table parseTable(String source) {
        Toml.Document doc = (Toml.Document) TomlParser.builder().build()
                .parse(source).findFirst().orElseThrow();
        return (Toml.Table) doc.getValues().get(0);
    }
}
