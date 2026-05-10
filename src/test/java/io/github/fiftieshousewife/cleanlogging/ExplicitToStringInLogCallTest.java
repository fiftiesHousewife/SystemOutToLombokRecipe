package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ExplicitToStringInLogCallTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExplicitToStringInLogCall())
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
    void dropsToStringFromSingleSubstitutionArg() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(Object obj) {
                                        log.info("x = {}", obj.toString());
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(Object obj) {
                                        log.info("x = {}", obj);
                                    }
                                }
                                """));
    }

    @Test
    void dropsToStringFromMultipleSubstitutionArgs() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(Object a, Object b) {
                                        log.warn("a={} b={}", a.toString(), b.toString());
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(Object a, Object b) {
                                        log.warn("a={} b={}", a, b);
                                    }
                                }
                                """));
    }

    @Test
    void dropsOnlyArgsThatHaveToString() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(String name, Object obj) {
                                        log.info("name={} obj={}", name, obj.toString());
                                    }
                                }
                                """,
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(String name, Object obj) {
                                        log.info("name={} obj={}", name, obj);
                                    }
                                }
                                """));
    }

    @Test
    void leavesAloneWhenNoToString() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(Object obj) {
                                        log.info("x = {}", obj);
                                    }
                                }
                                """));
    }

    @Test
    void leavesAloneWhenReceiverNotNamedLog() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger logger;
                                    void run(Object obj) {
                                        logger.info("x = {}", obj.toString());
                                    }
                                }
                                """));
    }

    @Test
    void leavesAloneToStringWithArguments() {
        rewriteRun(
                java(
                        """
                                import org.slf4j.Logger;
                                public class Foo {
                                    Logger log;
                                    void run(int[] arr) {
                                        log.info("x = {}", java.util.Arrays.toString(arr));
                                    }
                                }
                                """));
    }
}
