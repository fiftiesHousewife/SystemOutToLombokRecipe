package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.toml.Assertions.toml;

@DisplayName("DirectSlf4jLoggerFieldToLombokRecipe end-to-end with real GradleProject marker")
class DirectSlf4jLoggerFieldToLombokIntegrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi())
                .typeValidationOptions(TypeValidation.none())
                .parser(JavaParser.fromJavaVersion()
                        .dependsOn(
                                """
                                        package org.slf4j;
                                        public interface Logger {
                                            void info(String msg);
                                            void error(String msg);
                                            void warn(String msg);
                                            void debug(String msg);
                                            void trace(String msg);
                                        }
                                        """,
                                """
                                        package org.slf4j;
                                        public final class LoggerFactory {
                                            public static Logger getLogger(Class<?> c) { return null; }
                                            public static Logger getLogger(String name) { return null; }
                                        }
                                        """))
                .recipe(Environment.builder()
                        .scanRuntimeClasspath("io.github.fiftieshousewife")
                        .build()
                        .activateRecipes("io.github.fiftieshousewife.cleanlogging.DirectSlf4jLoggerFieldToLombokRecipe"));
    }

    @Test
    @DisplayName("inline (no catalog): manual SLF4J Logger field becomes @Slf4j; Lombok deps added")
    void inline_fieldRewrittenAndLombokDepsAdded() {
        rewriteRun(
                java(
                        """
                                package com.example;

                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Service {
                                    private static final Logger logger = LoggerFactory.getLogger(Service.class);

                                    public void run() {
                                        logger.info("starting");
                                    }
                                }
                                """,
                        """
                                package com.example;

                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Service {

                                    public void run() {
                                        log.info("starting");
                                    }
                                }
                                """
                ),
                buildGradleKts(
                        """
                                plugins {
                                    java
                                }
                                repositories {
                                    mavenCentral()
                                }
                                """,
                        """
                                plugins {
                                    java
                                }
                                repositories {
                                    mavenCentral()
                                }

                                dependencies {
                                    annotationProcessor("org.projectlombok:lombok:1.18.46")

                                    compileOnly("org.projectlombok:lombok:1.18.46")
                                }
                                """
                )
        );
    }

    @Test
    @DisplayName("catalog: lombok lands in libs.versions.toml; build.gradle.kts uses libs.lombok")
    void catalog_seedsCatalogAndReferencesLibsLombok() {
        rewriteRun(
                toml(
                        """
                                [versions]

                                [libraries]
                                """,
                        """
                                [versions]
                                lombok = "1.18.46"

                                [libraries]
                                lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }
                                """,
                        spec -> spec.path("gradle/libs.versions.toml")
                ),
                java(
                        """
                                package com.example;

                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Service {
                                    private static final Logger logger = LoggerFactory.getLogger(Service.class);

                                    public void run() {
                                        logger.info("starting");
                                    }
                                }
                                """,
                        """
                                package com.example;

                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Service {

                                    public void run() {
                                        log.info("starting");
                                    }
                                }
                                """
                ),
                buildGradleKts(
                        """
                                plugins {
                                    java
                                }
                                repositories {
                                    mavenCentral()
                                }
                                """,
                        """
                                plugins {
                                    java
                                }
                                repositories {
                                    mavenCentral()
                                }

                                dependencies {
                                    annotationProcessor(libs.lombok)

                                    compileOnly(libs.lombok)
                                }
                                """
                )
        );
    }
}
