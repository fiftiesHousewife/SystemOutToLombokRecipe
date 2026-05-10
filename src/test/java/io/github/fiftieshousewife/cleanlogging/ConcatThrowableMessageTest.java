package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ConcatThrowableMessageTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConcatThrowableMessage())
                .parser(JavaParser.fromJavaVersion()
                        .dependsOn(
                                """
                                        package org.slf4j;
                                        public interface Logger {
                                            void trace(String msg);
                                            void trace(String format, Object arg);
                                            void trace(String msg, Throwable t);
                                            void debug(String msg);
                                            void debug(String format, Object arg);
                                            void debug(String msg, Throwable t);
                                            void info(String msg);
                                            void info(String format, Object arg);
                                            void info(String msg, Throwable t);
                                            void warn(String msg);
                                            void warn(String format, Object arg);
                                            void warn(String msg, Throwable t);
                                            void error(String msg);
                                            void error(String format, Object arg);
                                            void error(String msg, Throwable t);
                                        }
                                        """,
                                """
                                        package org.slf4j;
                                        public final class LoggerFactory {
                                            public static Logger getLogger(Class<?> c) { return null; }
                                        }
                                        """));
    }

    @Test
    void errorPeelsTrailingGetMessage() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Exception e) {
                                        log.error("failed: " + e.getMessage());
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Exception e) {
                                        log.error("failed: ", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void warnPeelsTrailingGetMessage() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Exception e) {
                                        log.warn("trouble: " + e.getMessage());
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Exception e) {
                                        log.warn("trouble: ", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void infoPeelsTrailingGetMessage() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Exception e) {
                                        log.info("note: " + e.getMessage());
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Exception e) {
                                        log.info("note: ", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void debugPeelsTrailingGetMessage() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Exception e) {
                                        log.debug("detail: " + e.getMessage());
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Exception e) {
                                        log.debug("detail: ", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void tracePeelsTrailingGetMessage() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Exception e) {
                                        log.trace("verbose: " + e.getMessage());
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Exception e) {
                                        log.trace("verbose: ", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void multiPartLhsPreservedVerbatim() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(String id, Exception e) {
                                        log.error("processing " + id + " failed: " + e.getMessage());
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(String id, Exception e) {
                                        log.error("processing " + id + " failed: ", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void throwableSubtypeMatched() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(RuntimeException re) {
                                        log.error("oops: " + re.getMessage());
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(RuntimeException re) {
                                        log.error("oops: ", re);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void noOpWhenAlreadyTwoArgs() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Exception e) {
                                        log.error("failed", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void noOpWhenParameterized() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(String id) {
                                        log.info("processing {}", id);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void noOpWhenRhsIsNotGetMessage() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(String name) {
                                        log.info("hello " + name);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void noOpWhenSimpleStringLiteral() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run() {
                                        log.warn("static message");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void noOpWhenGetMessageOnNonThrowable() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                import org.slf4j.LoggerFactory;

                                class Wrapper {
                                    String getMessage() { return "x"; }
                                }

                                class Service {
                                    private static final Logger log = LoggerFactory.getLogger(Service.class);

                                    void run(Wrapper w) {
                                        log.info("data: " + w.getMessage());
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void noOpWhenReceiverIsNotSlf4jLogger() {
        rewriteRun(
                java(
                        """
                                class Other {
                                    void error(String msg) {}
                                }

                                class Service {
                                    private final Other other = new Other();

                                    void run(Exception e) {
                                        other.error("failed: " + e.getMessage());
                                    }
                                }
                                """
                )
        );
    }
}