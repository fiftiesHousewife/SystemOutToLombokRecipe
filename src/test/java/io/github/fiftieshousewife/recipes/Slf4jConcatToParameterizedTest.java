package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class Slf4jConcatToParameterizedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new Slf4jConcatToParameterized())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void parameterisesSingleVariableConcat() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    Object log;
                                    void greet(String userId) {
                                        log.info("user " + userId + " logged in");
                                    }
                                }
                                """,
                        """
                                public class Foo {
                                    Object log;
                                    void greet(String userId) {
                                        log.info("user {} logged in", userId);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void parameterisesMultipleVariables() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    Object log;
                                    void run(String userId, String action) {
                                        log.info("user " + userId + " did " + action);
                                    }
                                }
                                """,
                        """
                                public class Foo {
                                    Object log;
                                    void run(String userId, String action) {
                                        log.info("user {} did {}", userId, action);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void parameterisesAllSlf4jLevels() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    Object log;
                                    void run(String x) {
                                        log.trace("t=" + x);
                                        log.debug("d=" + x);
                                        log.info("i=" + x);
                                        log.warn("w=" + x);
                                        log.error("e=" + x);
                                    }
                                }
                                """,
                        """
                                public class Foo {
                                    Object log;
                                    void run(String x) {
                                        log.trace("t={}", x);
                                        log.debug("d={}", x);
                                        log.info("i={}", x);
                                        log.warn("w={}", x);
                                        log.error("e={}", x);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void parameterisesMethodCallExpression() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    Object log;
                                    String name() { return "bob"; }
                                    void run() {
                                        log.info("hi " + name());
                                    }
                                }
                                """,
                        """
                                public class Foo {
                                    Object log;
                                    String name() { return "bob"; }
                                    void run() {
                                        log.info("hi {}", name());
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void leavesPlainStringArgumentAlone() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    Object log;
                                    void run() {
                                        log.info("just a string");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void leavesAlreadyParameterizedCallAlone() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    Object log;
                                    void run(String x) {
                                        log.info("user {}", x);
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
                                public class Foo {
                                    Object logger;
                                    void run(String x) {
                                        logger.info("user " + x);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void leavesUnrelatedMethodNameAlone() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    Object log;
                                    void run(String x) {
                                        log.println("user " + x);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void leavesAllStringLiteralConcatAlone() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    Object log;
                                    void run() {
                                        log.info("a" + "b");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void leavesTwoArgCallAlone() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    Object log;
                                    void run(Exception e) {
                                        log.error("boom: " + e.getMessage(), e);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void parameterisesMixedConcatAndLiteralPrefix() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    Object log;
                                    void run(int n) {
                                        log.info("count=" + n + " end");
                                    }
                                }
                                """,
                        """
                                public class Foo {
                                    Object log;
                                    void run(int n) {
                                        log.info("count={} end", n);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void escapesQuotesAndBackslashesInTemplate() {
        rewriteRun(
                java(
                        """
                                public class Foo {
                                    Object log;
                                    void run(String x) {
                                        log.info("path: \\"" + x + "\\"");
                                    }
                                }
                                """,
                        """
                                public class Foo {
                                    Object log;
                                    void run(String x) {
                                        log.info("path: \\"{}\\"", x);
                                    }
                                }
                                """
                )
        );
    }
}
