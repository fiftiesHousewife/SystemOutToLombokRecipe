package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.toml.tree.Toml;

import java.util.function.Consumer;

import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.toml.Assertions.toml;

class UseCatalogReferenceForDependencyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseCatalogReferenceForDependency(
                "org.projectlombok:lombok", "libs.lombok"));
    }

    @Test
    void rewritesInlineStringToCatalogReferenceWhenCatalogPresent() {
        rewriteRun(
                toml(CATALOG_STUB, (Consumer<SourceSpec<Toml.Document>>) spec -> spec.path("gradle/libs.versions.toml")),
                buildGradleKts(
                        """
                                plugins {
                                    java
                                }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly("org.projectlombok:lombok:1.18.44")
                                }
                                """,
                        """
                                plugins {
                                    java
                                }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly(libs.lombok)
                                }
                                """
                )
        );
    }

    @Test
    void leavesInlineStringAloneWhenNoCatalog() {
        rewriteRun(
                buildGradleKts(
                        """
                                plugins {
                                    java
                                }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly("org.projectlombok:lombok:1.18.44")
                                }
                                """
                )
        );
    }

    @Test
    void ignoresDifferentModules() {
        rewriteRun(
                toml(CATALOG_STUB, (Consumer<SourceSpec<Toml.Document>>) spec -> spec.path("gradle/libs.versions.toml")),
                buildGradleKts(
                        """
                                plugins {
                                    java
                                }
                                repositories { mavenCentral() }
                                dependencies {
                                    implementation("org.apache.logging.log4j:log4j-api:2.25.4")
                                }
                                """
                )
        );
    }

    @Test
    void handlesAnnotationProcessor() {
        rewriteRun(
                toml(CATALOG_STUB, (Consumer<SourceSpec<Toml.Document>>) spec -> spec.path("gradle/libs.versions.toml")),
                buildGradleKts(
                        """
                                plugins {
                                    java
                                }
                                repositories { mavenCentral() }
                                dependencies {
                                    annotationProcessor("org.projectlombok:lombok:1.18.44")
                                }
                                """,
                        """
                                plugins {
                                    java
                                }
                                repositories { mavenCentral() }
                                dependencies {
                                    annotationProcessor(libs.lombok)
                                }
                                """
                )
        );
    }

    private static final String CATALOG_STUB = """
            [versions]
            lombok = "1.18.44"

            [libraries]
            lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }
            """;
}
