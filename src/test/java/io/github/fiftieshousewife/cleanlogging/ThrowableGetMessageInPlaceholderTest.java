package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ThrowableGetMessageInPlaceholderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ThrowableGetMessageInPlaceholder())
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
    void appendsThrowableWhenSingleArgIsGetMessage() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom() {
                                        try { risky(); } catch (Exception e) {
                                            log.error("oops: {}", e.getMessage());
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
                                            log.error("oops: {}", e.getMessage(), e);
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """));
    }

    @Test
    void appendsThrowableWhenMultipleArgsAndLastIsGetMessage() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom(String userId) {
                                        try { risky(); } catch (Exception e) {
                                            log.error("user {} failed: {}", userId, e.getMessage());
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
                                            log.error("user {} failed: {}", userId, e.getMessage(), e);
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """));
    }

    @Test
    void leavesAloneWhenThrowableAlreadyTrailingAfterGetMessage() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom() {
                                        try { risky(); } catch (Exception e) {
                                            log.error("oops: {}", e.getMessage(), e);
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """));
    }

    @Test
    void leavesAloneWhenLastArgIsThrowableNotGetMessage() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom() {
                                        try { risky(); } catch (Exception e) {
                                            log.error("oops: {}", e);
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """));
    }

    @Test
    void leavesAloneWhenPlaceholderCountDoesNotMatchArgs() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void boom() {
                                        try { risky(); } catch (Exception e) {
                                            log.error("oops", e.getMessage());
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """));
    }

    @Test
    void leavesAloneWhenGetMessageNotOnThrowable() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(Holder h) {
                                        log.info("got: {}", h.getMessage());
                                    }
                                    static class Holder {
                                        String getMessage() { return ""; }
                                    }
                                }
                                """));
    }

    @Test
    void leavesAloneWhenReceiverIsNotNamedLog() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger logger;
                                    void boom() {
                                        try { risky(); } catch (Exception e) {
                                            logger.error("oops: {}", e.getMessage());
                                        }
                                    }
                                    void risky() throws Exception { throw new Exception(); }
                                }
                                """));
    }
}
