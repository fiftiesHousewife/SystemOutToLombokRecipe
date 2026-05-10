package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateToCleanLoggingRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("io.github.fiftieshousewife")
                        .build()
                        .activateRecipes(
                                "io.github.fiftieshousewife.cleanlogging.CommonsLoggingToSlf4j",
                                "io.github.fiftieshousewife.cleanlogging.ConvertManualLoggerToSlf4j",
                                "io.github.fiftieshousewife.cleanlogging.JavaTransforms",
                                "io.github.fiftieshousewife.cleanlogging.Slf4jConcatToParameterized",
                                "io.github.fiftieshousewife.cleanlogging.ThrowableLastArgumentNoPlaceholder"))
                .parser(JavaParser.fromJavaVersion()
                        .dependsOn(
                                """
                                        package org.apache.commons.logging;
                                        public interface Log {
                                            void fatal(Object msg);
                                            void fatal(Object msg, Throwable t);
                                            void error(Object msg);
                                            void info(Object msg);
                                        }
                                        """,
                                """
                                        package org.apache.commons.logging;
                                        public final class LogFactory {
                                            public static Log getLog(Class<?> c) { return null; }
                                        }
                                        """))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesCommonsLoggingPlusSystemOutInOnePass() {
        rewriteRun(
                java(
                        """
                                package com.example;

                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Service {
                                    private static final Log log = LogFactory.getLog(Service.class);

                                    public void run(String user) {
                                        log.fatal("game over");
                                        log.info("user " + user + " ran");
                                        System.out.println("starting " + user);
                                    }
                                }
                                """,
                        """
                                package com.example;

                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Service {

                                    public void run(String user) {
                                        log.error("game over");
                                        log.info("user {} ran", user);
                                        log.info("starting {}", user);
                                    }
                                }
                                """));
    }
}
