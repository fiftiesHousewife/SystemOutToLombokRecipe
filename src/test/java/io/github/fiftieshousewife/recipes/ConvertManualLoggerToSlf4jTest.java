package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.Collections;
import java.util.List;

import static org.openrewrite.java.Assertions.java;

class ConvertManualLoggerToSlf4jTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConvertManualLoggerToSlf4j())
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
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
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
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
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
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
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
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
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

    @Test
    void skipsConversion_whenRequireLombokIsTrueAndClasspathLacksLombok() {
        rewriteRun(
                spec -> spec.recipe(new ConvertManualLoggerToSlf4j(true)),
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
                        spec -> spec.markers(sourceSetWith("java.util.List"))
                )
        );
    }

    @Test
    void convertsLogger_whenRequireLombokIsTrueAndClasspathContainsLombok() {
        rewriteRun(
                spec -> spec.recipe(new ConvertManualLoggerToSlf4j(true)),
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
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Foo {

                                    void run() {
                                        log.info("hello");
                                    }
                                }
                                """,
                        spec -> spec.markers(sourceSetWith(LoggerNames.LOMBOK_SLF4J))
                )
        );
    }

    private static JavaSourceSet sourceSetWith(String... fqTypes) {
        return new JavaSourceSet(Tree.randomId(), "main",
                java.util.Arrays.stream(fqTypes)
                        .<JavaType.FullyQualified>map(JavaType.ShallowClass::build)
                        .toList(),
                Collections.emptyMap());
    }
}
