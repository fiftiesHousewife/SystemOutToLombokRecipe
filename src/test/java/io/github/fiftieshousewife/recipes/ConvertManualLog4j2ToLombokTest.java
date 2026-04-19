package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ConvertManualLog4j2ToLombokTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConvertManualLog4j2ToLombok())
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
    void convertsLogFieldToLombokAnnotation() {
        rewriteRun(
                java(
                        """
                                import org.apache.logging.log4j.LogManager;
                                import org.apache.logging.log4j.Logger;

                                public class Foo {
                                    private static final Logger log = LogManager.getLogger(Foo.class);

                                    void run() {
                                        log.info("hello");
                                    }
                                }
                                """,
                        """
                                import lombok.extern.log4j.Log4j2;

                                @Log4j2
                                public class Foo {

                                    void run() {
                                        log.info("hello");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void renamesLoggerFieldToLog() {
        rewriteRun(
                java(
                        """
                                import org.apache.logging.log4j.LogManager;
                                import org.apache.logging.log4j.Logger;

                                public class Foo {
                                    private static final Logger logger = LogManager.getLogger(Foo.class);

                                    void run() {
                                        logger.info("hello");
                                        logger.error("oops");
                                    }
                                }
                                """,
                        """
                                import lombok.extern.log4j.Log4j2;

                                @Log4j2
                                public class Foo {

                                    void run() {
                                        log.info("hello");
                                        log.error("oops");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void renamesUppercaseLOG() {
        rewriteRun(
                java(
                        """
                                import org.apache.logging.log4j.LogManager;
                                import org.apache.logging.log4j.Logger;

                                public class Foo {
                                    private static final Logger LOG = LogManager.getLogger(Foo.class);

                                    void run() {
                                        LOG.info("hello");
                                    }
                                }
                                """,
                        """
                                import lombok.extern.log4j.Log4j2;

                                @Log4j2
                                public class Foo {

                                    void run() {
                                        log.info("hello");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsWhenAlreadyHasLombokAnnotation() {
        rewriteRun(
                java(
                        """
                                import lombok.extern.log4j.Log4j2;

                                @Log4j2
                                public class Foo {
                                    void run() {
                                        log.info("hello");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsWhenNoManualLog4j2Field() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    void run() {
                                        System.out.println("hi");
                                    }
                                }
                                """
                )
        );
    }
}
