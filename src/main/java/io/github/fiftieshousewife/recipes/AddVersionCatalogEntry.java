package io.github.fiftieshousewife.recipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.toml.TomlIsoVisitor;
import org.openrewrite.toml.tree.Toml;

import java.time.Duration;
import java.util.Set;

import static io.github.fiftieshousewife.recipes.CatalogDocumentEditor.CatalogEntry;
import static io.github.fiftieshousewife.recipes.CatalogDocumentEditor.updateDocument;

/**
 * Adds an entry to a Gradle version catalog ({@code gradle/libs.versions.toml}).
 * Adds a value to the {@code [versions]} table and a module binding to the
 * {@code [libraries]} table. If an alias is already present, does nothing.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class AddVersionCatalogEntry extends Recipe {

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
        final CatalogEntry entry = new CatalogEntry(versionAlias, versionValue, libraryAlias, module);
        return new TomlIsoVisitor<>() {
            @Override
            public Toml.Document visitDocument(final Toml.Document document, final ExecutionContext ctx) {
                if (!document.getSourcePath().toString().endsWith("libs.versions.toml")) {
                    return document;
                }
                return updateDocument(document, entry);
            }
        };
    }
}
