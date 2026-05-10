package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateToSlf4jRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("io.github.fiftieshousewife")
                        .build()
                        .activateRecipes(
                                "io.github.fiftieshousewife.cleanlogging.ConvertManualLoggerToSlf4j",
                                "io.github.fiftieshousewife.cleanlogging.JavaTransforms"))
                .parser(JavaParser.fromJavaVersion()
                        .dependsOn(
                                """
                                        package org.apache.logging.log4j;
                                        public interface Logger {
                                            void info(String msg);
                                            void error(String msg);
                                            void warn(String msg);
                                            void debug(String msg);
                                            void trace(String msg);
                                        }
                                        """,
                                """
                                        package org.apache.logging.log4j;
                                        public final class LogManager {
                                            public static Logger getLogger(Class<?> c) { return null; }
                                            public static Logger getLogger(String name) { return null; }
                                        }
                                        """))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesMixedLoggingPatternsInOnePass() {
        rewriteRun(
                java(
                        """
                                package com.example;

                                import java.util.logging.Logger;

                                public class MixedBag {
                                    private static final Logger jul = Logger.getLogger(MixedBag.class.getName());

                                    public void systemOut() {
                                        System.out.println("hello from stdout");
                                    }

                                    public void systemErr() {
                                        System.err.println("hello from stderr");
                                    }

                                    public void julCall() {
                                        jul.info("jul info");
                                        jul.severe("jul severe");
                                    }

                                    public void printStack(Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                """,
                        """
                                package com.example;

                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class MixedBag {

                                    public void systemOut() {
                                        log.info("hello from stdout");
                                    }

                                    public void systemErr() {
                                        log.error("hello from stderr");
                                    }

                                    public void julCall() {
                                        log.info("jul info");
                                        log.error("jul severe");
                                    }

                                    public void printStack(Exception e) {
                                        log.error("Exception occurred", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void migratesManualLog4j2AndSystemOutInSameClass() {
        rewriteRun(
                java(
                        """
                                package com.example;

                                import org.apache.logging.log4j.LogManager;
                                import org.apache.logging.log4j.Logger;

                                public class Mixed {
                                    private static final Logger logger = LogManager.getLogger(Mixed.class);

                                    public void existing() {
                                        logger.info("already structured");
                                    }

                                    public void legacy() {
                                        System.out.println("needs migrating");
                                    }
                                }
                                """,
                        """
                                package com.example;

                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Mixed {

                                    public void existing() {
                                        log.info("already structured");
                                    }

                                    public void legacy() {
                                        log.info("needs migrating");
                                    }
                                }
                                """
                )
        );
    }
}
