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

import static org.openrewrite.java.Assertions.java;

class DirectSlf4jLoggerFieldToLombokTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DirectSlf4jLoggerFieldToLombok(false))
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
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void convertsLogFieldToLombokAnnotation() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    private static final Logger log = LoggerFactory.getLogger(Foo.class);

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
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    private static final Logger logger = LoggerFactory.getLogger(Foo.class);

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
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    private static final Logger LOG = LoggerFactory.getLogger(Foo.class);

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
    void convertsPackagePrivateField() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    static final Logger log = LoggerFactory.getLogger(Foo.class);

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
    void convertsWhenInitialisedWithStringName() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    private static final Logger log = LoggerFactory.getLogger("Foo");

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
    void skipsWhenNoSlf4jField() {
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
    void skipsWhenFieldIsPublic() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    public static final Logger log = LoggerFactory.getLogger(Foo.class);

                                    void run() {
                                        log.info("hello");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsWhenFieldIsProtected() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    protected static final Logger log = LoggerFactory.getLogger(Foo.class);

                                    void run() {
                                        log.info("hello");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsWhenFieldIsNotStatic() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    private final Logger log = LoggerFactory.getLogger(Foo.class);

                                    void run() {
                                        log.info("hello");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsWhenFieldIsNotFinal() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    private static Logger log = LoggerFactory.getLogger(Foo.class);

                                    void run() {
                                        log.info("hello");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsWhenInitialiserIsNotLoggerFactory() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;

                                public class Foo {
                                    private static final Logger log = null;

                                    void run() {
                                        if (log != null) log.info("hello");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsWhenMoreThanOneEligibleField() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    private static final Logger log = LoggerFactory.getLogger(Foo.class);
                                    private static final Logger audit = LoggerFactory.getLogger("audit");

                                    void run() {
                                        log.info("hello");
                                        audit.info("audit");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsConversionWhenRequireLombokIsTrueAndClasspathLacksLombok() {
        rewriteRun(
                spec -> spec.recipe(new DirectSlf4jLoggerFieldToLombok(true)),
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    private static final Logger log = LoggerFactory.getLogger(Foo.class);

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
    void convertsLoggerWhenRequireLombokIsTrueAndClasspathContainsLombok() {
        rewriteRun(
                spec -> spec.recipe(new DirectSlf4jLoggerFieldToLombok(true)),
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                public class Foo {
                                    private static final Logger log = LoggerFactory.getLogger(Foo.class);

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
                        spec -> spec.markers(sourceSetWith(LombokLoggingAnnotation.SLF4J.fqn()))
                )
        );
    }

    static JavaSourceSet sourceSetWith(String... fqTypes) {
        return new JavaSourceSet(Tree.randomId(), "main",
                java.util.Arrays.stream(fqTypes)
                        .<JavaType.FullyQualified>map(JavaType.ShallowClass::build)
                        .toList(),
                Collections.emptyMap());
    }
}