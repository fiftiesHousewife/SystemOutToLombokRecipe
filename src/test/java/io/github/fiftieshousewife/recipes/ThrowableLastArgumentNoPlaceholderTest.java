package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ThrowableLastArgumentNoPlaceholderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ThrowableLastArgumentNoPlaceholder())
                .parser(JavaParser.fromJavaVersion()
                        .dependsOn(
                                """
                                        package org.slf4j;
                                        public interface Logger {
                                            void trace(String msg, Object... args);
                                            void debug(String msg, Object... args);
                                            void info(String msg, Object... args);
                                            void warn(String msg, Object... args);
                                            void error(String msg, Object... args);
                                        }
                                        """))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void dropsTrailingPlaceholderWhenSingleArgIsThrowable() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom() {
                                        try { risky(); } catch (Exception e) {
                                            log.error("failed: {}", e);
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom() {
                                        try { risky(); } catch (Exception e) {
                                            log.error("failed", e);
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """
                )
        );
    }

    @Test
    void dropsTrailingPlaceholderWhenMultipleArgsCountMatches() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom(String userId) {
                                        try { risky(); } catch (Exception e) {
                                            log.error("user {} failed: {}", userId, e);
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom(String userId) {
                                        try { risky(); } catch (Exception e) {
                                            log.error("user {} failed", userId, e);
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """
                )
        );
    }

    @Test
    void worksAcrossAllSlf4jLevels() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(Throwable t) {
                                        log.trace("a {}", t);
                                        log.debug("b {}", t);
                                        log.info("c {}", t);
                                        log.warn("d {}", t);
                                        log.error("e {}", t);
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(Throwable t) {
                                        log.trace("a", t);
                                        log.debug("b", t);
                                        log.info("c", t);
                                        log.warn("d", t);
                                        log.error("e", t);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void leavesCorrectUsageAlone() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom() {
                                        try { risky(); } catch (Exception e) {
                                            log.error("failed", e);
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """
                )
        );
    }

    @Test
    void leavesCallWithExtraArgAlone() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom(String userId) {
                                        try { risky(); } catch (Exception e) {
                                            log.error("user {} failed", userId, e);
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """
                )
        );
    }

    @Test
    void leavesCallWithoutTrailingThrowableAlone() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(String userId, String action) {
                                        log.info("user {} did {}", userId, action);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void leavesNonLogReceiverAlone() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger logger;
                                    void boom(Throwable e) {
                                        logger.error("failed: {}", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void leavesNonLiteralFirstArgumentAlone() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    String message() { return "msg {}"; }
                                    void boom(Throwable e) {
                                        log.error(message(), e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void leavesEscapedPlaceholderAlone() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom(Throwable e) {
                                        log.error("escaped: \\\\{}", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void trimsTrailingColonAndSpace() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom(Throwable e) {
                                        log.error("oops: {}", e);
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom(Throwable e) {
                                        log.error("oops", e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void leavesCallWithEmptyResultingMessageAlone() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom(Throwable e) {
                                        log.error("{}", e);
                                    }
                                }
                                """
                )
        );
    }
}
