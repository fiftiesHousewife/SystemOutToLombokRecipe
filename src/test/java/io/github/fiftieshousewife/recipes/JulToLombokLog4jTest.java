package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class JulToLombokLog4jTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JulToLombokLog4j())
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void infoIsConvertedToLogInfo() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void run() {
                                        logger.info("starting");
                                    }
                                }
                                """,
                        """
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void run() {
                                        log.info("starting");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void severeBecomesError() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void fail(Exception e) {
                                        logger.severe("boom");
                                    }
                                }
                                """,
                        """
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void fail(Exception e) {
                                        log.error("boom");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void warningBecomesWarn() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void soft() {
                                        logger.warning("careful");
                                    }
                                }
                                """,
                        """
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void soft() {
                                        log.warn("careful");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void fineBecomesDebug() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void chatty() {
                                        logger.fine("verbose");
                                    }
                                }
                                """,
                        """
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void chatty() {
                                        log.debug("verbose");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void finestBecomesTrace() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void trace() {
                                        logger.finest("deep");
                                    }
                                }
                                """,
                        """
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void trace() {
                                        log.trace("deep");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void nonJulCallsAreUntouched() {
        rewriteRun(
                java(
                        """
                                public class Service {
                                    void run() {
                                        System.out.println("hi");
                                    }
                                }
                                """
                )
        );
    }
}
