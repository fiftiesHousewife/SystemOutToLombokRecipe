package io.github.fiftieshousewife.recipes.matrix;

import io.github.fiftieshousewife.recipes.UseCatalogReferenceForDependency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static io.github.fiftieshousewife.recipes.matrix.MatrixTestSupport.CATALOG_STUB;
import static io.github.fiftieshousewife.recipes.matrix.MatrixTestSupport.PROPERTIES_STUB;
import static io.github.fiftieshousewife.recipes.matrix.MatrixTestSupport.gradleProjectMarker;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.settingsGradle;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.toml.Assertions.toml;

@DisplayName("Matrix: Groovy DSL × (single/multi/build-logic) × (catalog/inline/properties)")
class GroovyDslMatrixTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseCatalogReferenceForDependency("org.projectlombok:lombok", "libs.lombok"));
    }

    @Test
    @DisplayName("single-module × catalog: inline dep is rewritten to libs.lombok")
    void singleModule_catalog() {
        rewriteRun(
                toml(CATALOG_STUB, spec -> spec.path("gradle/libs.versions.toml")),
                buildGradle(
                        """
                                plugins { id 'java' }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly 'org.projectlombok:lombok:1.18.44'
                                }
                                """,
                        """
                                plugins { id 'java' }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly libs.lombok
                                }
                                """
                )
        );
    }

    @Test
    @DisplayName("single-module × inline (no catalog): inline dep is left alone")
    void singleModule_inline() {
        rewriteRun(
                buildGradle(
                        """
                                plugins { id 'java' }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly 'org.projectlombok:lombok:1.18.44'
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
                buildGradle(
                        """
                                plugins { id 'java' }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly "org.projectlombok:lombok:${lombokVersion}"
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
                settingsGradle(
                        """
                                rootProject.name = 'root'
                                include 'app', 'lib'
                                """
                ),
                buildGradle(
                        """
                                plugins { id 'java' }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly 'org.projectlombok:lombok:1.18.44'
                                }
                                """,
                        """
                                plugins { id 'java' }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly libs.lombok
                                }
                                """,
                        spec -> spec.path("app/build.gradle").markers(gradleProjectMarker(":app"))
                ),
                buildGradle(
                        """
                                plugins { id 'java' }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly 'org.projectlombok:lombok:1.18.44'
                                }
                                """,
                        """
                                plugins { id 'java' }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly libs.lombok
                                }
                                """,
                        spec -> spec.path("lib/build.gradle").markers(gradleProjectMarker(":lib"))
                )
        );
    }

    @Test
    @DisplayName("multi-module × inline: both subprojects left alone")
    void multiModule_inline() {
        rewriteRun(
                settingsGradle(
                        """
                                rootProject.name = 'root'
                                include 'app', 'lib'
                                """
                ),
                buildGradle(
                        """
                                plugins { id 'java' }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly 'org.projectlombok:lombok:1.18.44'
                                }
                                """,
                        spec -> spec.path("app/build.gradle").markers(gradleProjectMarker(":app"))
                ),
                buildGradle(
                        """
                                plugins { id 'java' }
                                repositories { mavenCentral() }
                                dependencies {
                                    compileOnly 'org.projectlombok:lombok:1.18.44'
                                }
                                """,
                        spec -> spec.path("lib/build.gradle").markers(gradleProjectMarker(":lib"))
                )
        );
    }

    @Test
    @DisplayName("build-logic composite × nested catalog: convention plugin migrates when build-logic has its own catalog")
    void buildLogic_nestedCatalog() {
        rewriteRun(
                toml(CATALOG_STUB, spec -> spec.path("build-logic/gradle/libs.versions.toml")),
                buildGradle(
                        """
                                plugins { id 'groovy-gradle-plugin' }
                                repositories { mavenCentral() }
                                dependencies {
                                    implementation 'org.projectlombok:lombok:1.18.44'
                                }
                                """,
                        """
                                plugins { id 'groovy-gradle-plugin' }
                                repositories { mavenCentral() }
                                dependencies {
                                    implementation libs.lombok
                                }
                                """,
                        spec -> spec.path("build-logic/build.gradle")
                )
        );
    }

    @Test
    @DisplayName("buildSrc × catalog: buildSrc/build.gradle migrates when the root catalog is present")
    void buildSrc_rootCatalog() {
        rewriteRun(
                toml(CATALOG_STUB, spec -> spec.path("gradle/libs.versions.toml")),
                buildGradle(
                        """
                                plugins { id 'groovy-gradle-plugin' }
                                repositories { mavenCentral() }
                                dependencies {
                                    implementation 'org.projectlombok:lombok:1.18.44'
                                }
                                """,
                        """
                                plugins { id 'groovy-gradle-plugin' }
                                repositories { mavenCentral() }
                                dependencies {
                                    implementation libs.lombok
                                }
                                """,
                        spec -> spec.path("buildSrc/build.gradle")
                )
        );
    }
}
