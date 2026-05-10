package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.toml.Assertions.toml;

class AddVersionCatalogEntryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddVersionCatalogEntry("lombok", "1.18.44", "lombok", "org.projectlombok:lombok"));
    }

    @Test
    void addsToExistingEmptyTables() {
        rewriteRun(
                toml(
                        """
                                [versions]

                                [libraries]
                                """,
                        """
                                [versions]
                                lombok = "1.18.44"

                                [libraries]
                                lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }
                                """,
                        spec -> spec.path("gradle/libs.versions.toml")
                )
        );
    }

    @Test
    void addsToPopulatedTables() {
        rewriteRun(
                toml(
                        """
                                [versions]
                                junit = "5.10.0"

                                [libraries]
                                junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
                                """,
                        """
                                [versions]
                                junit = "5.10.0"
                                lombok = "1.18.44"

                                [libraries]
                                junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
                                lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }
                                """,
                        spec -> spec.path("gradle/libs.versions.toml")
                )
        );
    }

    @Test
    void skipsWhenAlreadyPresent() {
        rewriteRun(
                toml(
                        """
                                [versions]
                                lombok = "1.18.44"

                                [libraries]
                                lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }
                                """,
                        spec -> spec.path("gradle/libs.versions.toml")
                )
        );
    }

    @Test
    void ignoresOtherTomlFiles() {
        rewriteRun(
                toml(
                        """
                                [versions]

                                [libraries]
                                """,
                        spec -> spec.path("some-other.toml")
                )
        );
    }

}
