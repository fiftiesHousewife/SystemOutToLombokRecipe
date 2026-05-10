package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class StringFormatInLogCallTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new StringFormatInLogCall())
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
    void rewritesSimpleStringFormat() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(String user) {
                                        log.info(String.format("user %s logged in", user));
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(String user) {
                                        log.info("user {} logged in", user);
                                    }
                                }
                                """));
    }

    @Test
    void rewritesMultipleArgsAndIntSpecifier() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(String user, int count) {
                                        log.error(String.format("user %s saw %d items", user, count));
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(String user, int count) {
                                        log.error("user {} saw {} items", user, count);
                                    }
                                }
                                """));
    }

    @Test
    void rewritesNoSubstitutionsWithPercentN() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run() {
                                        log.info(String.format("done%n"));
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run() {
                                        log.info("done");
                                    }
                                }
                                """));
    }

    @Test
    void leavesAloneNonStringFormatCall() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(String user) {
                                        log.info("plain message");
                                    }
                                }
                                """));
    }

    @Test
    void leavesAloneVariableInsteadOfLiteralFormat() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(String user, String fmt) {
                                        log.info(String.format(fmt, user));
                                    }
                                }
                                """));
    }

    @Test
    void leavesAloneReceiverNotNamedLog() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger logger;
                                    void run(String user) {
                                        logger.info(String.format("user %s logged in", user));
                                    }
                                }
                                """));
    }
}
