package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradleKts;

class UseCatalogReferenceForDependencyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseCatalogReferenceForDependency(
                "org.projectlombok:lombok", "libs.lombok"));
    }

    @Test
    void rewritesInlineStringToCatalogReference() {
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
    void ignoresDifferentModules() {
        rewriteRun(
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
}
