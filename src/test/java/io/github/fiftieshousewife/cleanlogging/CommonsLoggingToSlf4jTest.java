package io.github.fiftieshousewife.cleanlogging;

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

class CommonsLoggingToSlf4jTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CommonsLoggingToSlf4j(false))
                .parser(commonsLoggingParser())
                .typeValidationOptions(TypeValidation.none());
    }

    private static JavaParser.Builder<?, ?> commonsLoggingParser() {
        return JavaParser.fromJavaVersion()
                .dependsOn(
                        """
                                package org.apache.commons.logging;
                                public interface Log {
                                    void fatal(Object msg);
                                    void fatal(Object msg, Throwable t);
                                    void error(Object msg);
                                    void error(Object msg, Throwable t);
                                    void warn(Object msg);
                                    void info(Object msg);
                                    void debug(Object msg);
                                    void trace(Object msg);
                                    boolean isFatalEnabled();
                                    boolean isErrorEnabled();
                                    boolean isWarnEnabled();
                                    boolean isInfoEnabled();
                                    boolean isDebugEnabled();
                                    boolean isTraceEnabled();
                                }
                                """,
                        """
                                package org.apache.commons.logging;
                                public final class LogFactory {
                                    public static Log getLog(Class<?> c) { return null; }
                                    public static Log getLog(String name) { return null; }
                                }
                                """);
    }

    @Test
    void convertsLogFieldToLombokAnnotation() {
        rewriteRun(
                java(
                        """
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static final Log log = LogFactory.getLog(Foo.class);

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
    void rewritesFatalToError() {
        rewriteRun(
                java(
                        """
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static final Log log = LogFactory.getLog(Foo.class);

                                    void boom() {
                                        log.fatal("game over");
                                    }
                                }
                                """,
                        """
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Foo {

                                    void boom() {
                                        log.error("game over");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void rewritesFatalWithThrowableToError() {
        rewriteRun(
                java(
                        """
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static final Log log = LogFactory.getLog(Foo.class);

                                    void boom(Exception e) {
                                        log.fatal("game over", e);
                                    }
                                }
                                """,
                        """
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Foo {

                                    void boom(Exception e) {
                                        log.error("game over", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void rewritesIsFatalEnabledToIsErrorEnabled() {
        rewriteRun(
                java(
                        """
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static final Log log = LogFactory.getLog(Foo.class);

                                    boolean check() {
                                        return log.isFatalEnabled();
                                    }
                                }
                                """,
                        """
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Foo {

                                    boolean check() {
                                        return log.isErrorEnabled();
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void leavesNameMatchedLevelMethodsUntouched() {
        rewriteRun(
                java(
                        """
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static final Log log = LogFactory.getLog(Foo.class);

                                    void run() {
                                        log.error("e");
                                        log.warn("w");
                                        log.info("i");
                                        log.debug("d");
                                        log.trace("t");
                                    }
                                }
                                """,
                        """
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Foo {

                                    void run() {
                                        log.error("e");
                                        log.warn("w");
                                        log.info("i");
                                        log.debug("d");
                                        log.trace("t");
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
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static final Log logger = LogFactory.getLog(Foo.class);

                                    void run() {
                                        logger.info("hi");
                                        logger.fatal("boom");
                                    }
                                }
                                """,
                        """
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Foo {

                                    void run() {
                                        log.info("hi");
                                        log.error("boom");
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
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static final Log LOG = LogFactory.getLog(Foo.class);

                                    void run() {
                                        LOG.info("hi");
                                    }
                                }
                                """,
                        """
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Foo {

                                    void run() {
                                        log.info("hi");
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
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    static final Log log = LogFactory.getLog(Foo.class);

                                    void run() {
                                        log.info("hi");
                                    }
                                }
                                """,
                        """
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Foo {

                                    void run() {
                                        log.info("hi");
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
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static final Log log = LogFactory.getLog("custom-name");

                                    void run() {
                                        log.info("hi");
                                    }
                                }
                                """,
                        """
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Foo {

                                    void run() {
                                        log.info("hi");
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
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                @Slf4j
                                public class Foo {
                                    private static final Log other = LogFactory.getLog(Foo.class);

                                    void run() {
                                        other.info("hi");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsWhenNoField() {
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
    void skipsPublicField() {
        rewriteRun(
                java(
                        """
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    public static final Log log = LogFactory.getLog(Foo.class);

                                    void run() {
                                        log.info("hi");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsNonStaticField() {
        rewriteRun(
                java(
                        """
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private final Log log = LogFactory.getLog(Foo.class);

                                    void run() {
                                        log.info("hi");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsNonFinalField() {
        rewriteRun(
                java(
                        """
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static Log log = LogFactory.getLog(Foo.class);

                                    void run() {
                                        log.info("hi");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsWhenInitialiserIsNotLogFactory() {
        rewriteRun(
                java(
                        """
                                import org.apache.commons.logging.Log;

                                public class Foo {
                                    private static final Log log = customLogFactory();

                                    static Log customLogFactory() { return null; }

                                    void run() {
                                        log.info("hi");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsWhenMultipleEligibleFields() {
        rewriteRun(
                java(
                        """
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static final Log log1 = LogFactory.getLog(Foo.class);
                                    private static final Log log2 = LogFactory.getLog("other");

                                    void run() {
                                        log1.info("a");
                                        log2.info("b");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void skipsConversionWhenRequireLombokIsTrueAndClasspathLacksLombok() {
        rewriteRun(
                spec -> spec.recipe(new CommonsLoggingToSlf4j(true)),
                java(
                        """
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static final Log log = LogFactory.getLog(Foo.class);

                                    void run() {
                                        log.info("hi");
                                        log.fatal("nope");
                                    }
                                }
                                """,
                        spec -> spec.markers(sourceSetWith("java.util.List"))
                )
        );
    }

    @Test
    void convertsWhenRequireLombokIsTrueAndClasspathContainsLombok() {
        rewriteRun(
                spec -> spec.recipe(new CommonsLoggingToSlf4j(true)),
                java(
                        """
                                import org.apache.commons.logging.Log;
                                import org.apache.commons.logging.LogFactory;

                                public class Foo {
                                    private static final Log log = LogFactory.getLog(Foo.class);

                                    void run() {
                                        log.info("hi");
                                        log.fatal("uh oh");
                                    }
                                }
                                """,
                        """
                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Foo {

                                    void run() {
                                        log.info("hi");
                                        log.error("uh oh");
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
