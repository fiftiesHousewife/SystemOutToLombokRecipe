package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.toml.TomlParser;
import org.openrewrite.toml.tree.Toml;
import org.openrewrite.toml.tree.TomlValue;

import java.util.List;

import static io.github.fiftieshousewife.recipes.AddVersionCatalogEntry.CatalogTable.LIBRARIES;
import static io.github.fiftieshousewife.recipes.AddVersionCatalogEntry.CatalogTable.VERSIONS;
import static io.github.fiftieshousewife.recipes.AddVersionCatalogEntry.addIfMissing;
import static io.github.fiftieshousewife.recipes.AddVersionCatalogEntry.addRow;
import static io.github.fiftieshousewife.recipes.AddVersionCatalogEntry.appendRowIfMatchingTable;
import static io.github.fiftieshousewife.recipes.AddVersionCatalogEntry.containsTableNamed;
import static io.github.fiftieshousewife.recipes.AddVersionCatalogEntry.leadingNewline;
import static io.github.fiftieshousewife.recipes.AddVersionCatalogEntry.parseKeyValue;
import static io.github.fiftieshousewife.recipes.AddVersionCatalogEntry.parseTable;
import static io.github.fiftieshousewife.recipes.AddVersionCatalogEntry.tableHasKey;
import static io.github.fiftieshousewife.recipes.AddVersionCatalogEntry.tableNameMatches;
import static org.assertj.core.api.Assertions.assertThat;

class AddVersionCatalogEntryMethodTest {

    @Test
    void parseKeyValue_returnsParsedKeyValue() {
        Toml.KeyValue kv = parseKeyValue("lombok = \"1.18.44\"");
        assertThat(kv.getKey()).isInstanceOfSatisfying(Toml.Identifier.class,
                id -> assertThat(id.getName()).isEqualTo("lombok"));
    }

    @Test
    void parseTable_returnsParsedTable() {
        Toml.Table table = parseTable("[versions]\nlombok = \"1.18.44\"\n");
        assertThat(table.getName()).isNotNull();
        assertThat(table.getName().getName()).isEqualTo("versions");
    }

    @Test
    void leadingNewline_returnsNewlineSpace() {
        assertThat(leadingNewline().getWhitespace()).isEqualTo("\n");
    }

    @Test
    void tableNameMatches_trueForMatchingName() {
        Toml.Table versions = parseTable("[versions]\n");
        assertThat(tableNameMatches(versions, VERSIONS)).isTrue();
    }

    @Test
    void tableNameMatches_falseForDifferentName() {
        Toml.Table libraries = parseTable("[libraries]\n");
        assertThat(tableNameMatches(libraries, VERSIONS)).isFalse();
    }

    @Test
    void containsTableNamed_trueWhenPresent() {
        List<TomlValue> values = parseValues("[versions]\n");
        assertThat(containsTableNamed(values, VERSIONS)).isTrue();
    }

    @Test
    void containsTableNamed_falseWhenAbsent() {
        List<TomlValue> values = parseValues("[libraries]\n");
        assertThat(containsTableNamed(values, VERSIONS)).isFalse();
    }

    @Test
    void tableHasKey_trueWhenKeyPresent() {
        List<TomlValue> values = parseValues("[versions]\nlombok = \"1.18.44\"\n");
        assertThat(tableHasKey(values, VERSIONS, "lombok")).isTrue();
    }

    @Test
    void tableHasKey_falseWhenKeyMissing() {
        List<TomlValue> values = parseValues("[versions]\nlombok = \"1.18.44\"\n");
        assertThat(tableHasKey(values, VERSIONS, "junit")).isFalse();
    }

    @Test
    void tableHasKey_falseWhenTableMissing() {
        List<TomlValue> values = parseValues("[libraries]\nlombok = { module = \"x:y\" }\n");
        assertThat(tableHasKey(values, VERSIONS, "lombok")).isFalse();
    }

    @Test
    void appendRowIfMatchingTable_appendsToMatchingTable() {
        Toml.Table versions = parseTable("[versions]\nexisting = \"1.0\"\n");
        TomlValue updated = appendRowIfMatchingTable(versions, VERSIONS, "added = \"2.0\"");
        assertThat(updated).isInstanceOfSatisfying(Toml.Table.class,
                t -> assertThat(t.getValues()).hasSize(2));
    }

    @Test
    void appendRowIfMatchingTable_returnsUnchangedForOtherTable() {
        Toml.Table libraries = parseTable("[libraries]\n");
        TomlValue result = appendRowIfMatchingTable(libraries, VERSIONS, "foo = \"bar\"");
        assertThat(result).isSameAs(libraries);
    }

    @Test
    void addRow_appendsToExistingTable() {
        List<TomlValue> values = parseValues("[versions]\nexisting = \"1.0\"\n");
        List<TomlValue> updated = addRow(values, VERSIONS, "added = \"2.0\"");
        assertThat(tableHasKey(updated, VERSIONS, "added")).isTrue();
        assertThat(tableHasKey(updated, VERSIONS, "existing")).isTrue();
    }

    @Test
    void addRow_createsNewTableWhenMissing() {
        List<TomlValue> values = parseValues("[libraries]\n");
        List<TomlValue> updated = addRow(values, VERSIONS, "added = \"2.0\"");
        assertThat(containsTableNamed(updated, VERSIONS)).isTrue();
        assertThat(tableHasKey(updated, VERSIONS, "added")).isTrue();
    }

    @Test
    void addIfMissing_returnsSameListWhenKeyPresent() {
        List<TomlValue> values = parseValues("[versions]\nlombok = \"1.18.44\"\n");
        List<TomlValue> result = addIfMissing(values, VERSIONS, "lombok", "lombok = \"9.9.9\"");
        assertThat(result).isSameAs(values);
    }

    @Test
    void addIfMissing_addsRowWhenKeyAbsent() {
        List<TomlValue> values = parseValues("[versions]\n");
        List<TomlValue> result = addIfMissing(values, VERSIONS, "lombok", "lombok = \"1.18.44\"");
        assertThat(result).isNotSameAs(values);
        assertThat(tableHasKey(result, VERSIONS, "lombok")).isTrue();
    }

    @Test
    void addIfMissing_createsLibrariesTableWhenAbsent() {
        List<TomlValue> values = parseValues("[versions]\n");
        List<TomlValue> result = addIfMissing(values, LIBRARIES, "lombok",
                "lombok = { module = \"org.projectlombok:lombok\" }");
        assertThat(containsTableNamed(result, LIBRARIES)).isTrue();
    }

    private static List<TomlValue> parseValues(String source) {
        return ((Toml.Document) TomlParser.builder().build()
                .parse(source).findFirst().orElseThrow()).getValues();
    }
}
