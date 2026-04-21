package io.github.fiftieshousewife.recipes.matrix;

import io.github.fiftieshousewife.recipes.UseCatalogReferenceForDependency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static io.github.fiftieshousewife.recipes.matrix.MatrixTestSupport.CATALOG_STUB;
import static io.github.fiftieshousewife.recipes.matrix.MatrixTestSupport.PROPERTIES_STUB;
import static io.github.fiftieshousewife.recipes.matrix.MatrixTestSupport.gradleProjectMarker;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.Assertions.settingsGradleKts;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.toml.Assertions.toml;

@DisplayName("Matrix: Kotlin DSL × (single/multi/build-logic) × (catalog/inline/properties)")
class KotlinDslMatrixTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseCatalogReferenceForDependency("org.projectlombok:lombok", "libs.lombok"));
    }

    @Test
    @DisplayName("single-module × catalog: inline dep is rewritten to libs.lombok")
    void singleModule_catalog() {
        rewriteRun(
                toml(CATALOG_STUB, spec -> spec.path("gradle/libs.versions.toml")),
                buildGradleKts(
                        """
                                plugins { java }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly("org.projectlombok:lombok:1.18.44")
                                }
                                """,
                        """
                                plugins { java }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly(libs.lombok)
                                }
                                """
                )
        );
    }

    @Test
    @DisplayName("single-module × inline (no catalog): inline dep is left alone")
    void singleModule_inline() {
        rewriteRun(
                buildGradleKts(
                        """
                                plugins { java }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly("org.projectlombok:lombok:1.18.44")
                                }
                                """
                )
        );
    }

    @Test
    @DisplayName("single-module × gradle.properties-driven version: interpolated literal left alone")
    void singleModule_propertiesVersion() {
        rewriteRun(
                properties(PROPERTIES_STUB, spec -> spec.path("gradle.properties")),
                buildGradleKts(
                        """
                                plugins { java }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly("org.projectlombok:lombok:$lombokVersion")
                                }
                                """
                )
        );
    }

    @Test
    @DisplayName("multi-module × catalog: both subprojects migrate to libs.lombok")
    void multiModule_catalog() {
        rewriteRun(
                toml(CATALOG_STUB, spec -> spec.path("gradle/libs.versions.toml")),
                settingsGradleKts(
                        """
                                rootProject.name = "root"
                                include("app", "lib")
                                """
                ),
                buildGradleKts(
                        """
                                plugins { java }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly("org.projectlombok:lombok:1.18.44")
                                }
                                """,
                        """
                                plugins { java }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly(libs.lombok)
                                }
                                """,
                        spec -> spec.path("app/build.gradle.kts").markers(gradleProjectMarker(":app"))
                ),
                buildGradleKts(
                        """
                                plugins { java }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly("org.projectlombok:lombok:1.18.44")
                                }
                                """,
                        """
                                plugins { java }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly(libs.lombok)
                                }
                                """,
                        spec -> spec.path("lib/build.gradle.kts").markers(gradleProjectMarker(":lib"))
                )
        );
    }

    @Test
    @DisplayName("multi-module × inline: both subprojects left alone")
    void multiModule_inline() {
        rewriteRun(
                settingsGradleKts(
                        """
                                rootProject.name = "root"
                                include("app", "lib")
                                """
                ),
                buildGradleKts(
                        """
                                plugins { java }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly("org.projectlombok:lombok:1.18.44")
                                }
                                """,
                        spec -> spec.path("app/build.gradle.kts").markers(gradleProjectMarker(":app"))
                ),
                buildGradleKts(
                        """
                                plugins { java }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly("org.projectlombok:lombok:1.18.44")
                                }
                                """,
                        spec -> spec.path("lib/build.gradle.kts").markers(gradleProjectMarker(":lib"))
                )
        );
    }

    @Test
    @DisplayName("build-logic composite × nested catalog: convention plugin migrates when build-logic has its own catalog")
    void buildLogic_nestedCatalog() {
        rewriteRun(
                toml(CATALOG_STUB, spec -> spec.path("build-logic/gradle/libs.versions.toml")),
                buildGradleKts(
                        """
                                plugins { `kotlin-dsl` }
                                repositories { mavenCentral() }
                                dependencies {
                                    implementation("org.projectlombok:lombok:1.18.44")
                                }
                                """,
                        """
                                plugins { `kotlin-dsl` }
                                repositories { mavenCentral() }
                                dependencies {
                                    implementation(libs.lombok)
                                }
                                """,
                        spec -> spec.path("build-logic/build.gradle.kts")
                )
        );
    }

    @Test
    @DisplayName("buildSrc × catalog: buildSrc/build.gradle.kts migrates when the root catalog is present")
    void buildSrc_rootCatalog() {
        rewriteRun(
                toml(CATALOG_STUB, spec -> spec.path("gradle/libs.versions.toml")),
                buildGradleKts(
                        """
                                plugins { `kotlin-dsl` }
                                repositories { mavenCentral() }
                                dependencies {
                                    implementation("org.projectlombok:lombok:1.18.44")
                                }
                                """,
                        """
                                plugins { `kotlin-dsl` }
                                repositories { mavenCentral() }
                                dependencies {
                                    implementation(libs.lombok)
                                }
                                """,
                        spec -> spec.path("buildSrc/build.gradle.kts")
                )
        );
    }
}
