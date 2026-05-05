package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class JulToSlf4jTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JulToSlf4j(false))
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void infoIsConvertedAndJulFieldRemoved() {
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



                                public class Service {

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



                                public class Service {

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



                                public class Service {

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



                                public class Service {

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



                                public class Service {

                                    void trace() {
                                        log.trace("deep");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void julFieldRemovedWhenIsLoggableAlsoConverted() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Level;
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void run() {
                                        logger.info("standard");
                                        if (logger.isLoggable(Level.FINE)) {
                                            logger.fine("expensive");
                                        }
                                    }
                                }
                                """,
                        """



                                public class Service {

                                    void run() {
                                        log.info("standard");
                                        if (log.isDebugEnabled()) {
                                            log.debug("expensive");
                                        }
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void julFieldKeptWhenIsLoggableUsesUnknownLevelVariable() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Level;
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void run(Level level) {
                                        logger.info("standard");
                                        if (logger.isLoggable(level)) {
                                            logger.fine("expensive");
                                        }
                                    }
                                }
                                """,
                        """
                                import java.util.logging.Level;
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void run(Level level) {
                                        log.info("standard");
                                        if (logger.isLoggable(level)) {
                                            log.debug("expensive");
                                        }
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void supplierLambdaUnwrappedToBodyExpression() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void run(String value) {
                                        logger.fine(() -> "value=" + value);
                                    }
                                }
                                """,
                        """



                                public class Service {

                                    void run(String value) {
                                        log.debug("value=" + value);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void supplierLambdaWithBlockBodyIsLeftAlone() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Logger;
                                import java.util.function.Supplier;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    void run(String value) {
                                        Supplier<String> supplier = () -> {
                                            String prefix = "value=";
                                            return prefix + value;
                                        };
                                        logger.fine(supplier);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void isLoggableLevelInfoBecomesIsInfoEnabled() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Level;
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    boolean check() {
                                        return logger.isLoggable(Level.INFO);
                                    }
                                }
                                """,
                        """



                                public class Service {

                                    boolean check() {
                                        return log.isInfoEnabled();
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void isLoggableLevelSevereBecomesIsErrorEnabled() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Level;
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    boolean check() {
                                        return logger.isLoggable(Level.SEVERE);
                                    }
                                }
                                """,
                        """



                                public class Service {

                                    boolean check() {
                                        return log.isErrorEnabled();
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void isLoggableLevelFinestBecomesIsTraceEnabled() {
        rewriteRun(
                java(
                        """
                                import java.util.logging.Level;
                                import java.util.logging.Logger;

                                public class Service {
                                    private static final Logger logger = Logger.getLogger(Service.class.getName());

                                    boolean check() {
                                        return logger.isLoggable(Level.FINEST);
                                    }
                                }
                                """,
                        """



                                public class Service {

                                    boolean check() {
                                        return log.isTraceEnabled();
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
