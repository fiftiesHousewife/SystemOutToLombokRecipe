package io.github.fiftieshousewife.smoketest;

import java.util.List;

/**
 * A single Java source file dropped into the throwaway project before the
 * recipe runs. The recipe transforms the body; we only verify that the
 * post-rewrite project still compiles.
 *
 * <p>{@code prerequisiteImplementationDeps} declares any classpath deps the
 * fixture's <em>original</em> source needs to compile (e.g. the manual-Log4j2
 * fixture imports {@code LogManager}, so {@code log4j-api} must be on the
 * smoke project's {@code implementation} configuration before the rewrite
 * runs — otherwise OpenRewrite can't resolve the types and the recipe matches
 * nothing).
 */
record Fixture(String relativePath, String body, List<String> prerequisiteImplementationDeps) {

    static final Fixture GREETING_SYSTEM_OUT = new Fixture(
            "src/main/java/com/example/Greeting.java",
            """
                    package com.example;

                    public class Greeting {
                        public void say(String name) {
                            System.out.println("Hello, " + name);
                        }

                        public void fail(Exception e) {
                            e.printStackTrace();
                        }
                    }
                    """,
            List.of());

    static final Fixture ORDER_SERVICE_MANUAL_LOG4J2 = new Fixture(
            "src/main/java/com/example/OrderService.java",
            """
                    package com.example;

                    import org.apache.logging.log4j.LogManager;
                    import org.apache.logging.log4j.Logger;

                    public class OrderService {
                        private static final Logger logger = LogManager.getLogger(OrderService.class);

                        public void placeOrder(String id) {
                            logger.info("Placing " + id);
                        }
                    }
                    """,
            List.of("org.apache.logging.log4j:log4j-api:2.25.4"));

    static final Fixture ORDER_SERVICE_SLF4J_DIRECT = new Fixture(
            "src/main/java/com/example/OrderService.java",
            """
                    package com.example;

                    import org.slf4j.Logger;
                    import org.slf4j.LoggerFactory;

                    public class OrderService {
                        private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

                        public void placeOrder(String id) {
                            logger.info("Placing " + id);
                        }
                    }
                    """,
            List.of("org.slf4j:slf4j-api:2.0.17"));

    static final Fixture ORDER_SERVICE_COMMONS_LOGGING = new Fixture(
            "src/main/java/com/example/OrderService.java",
            """
                    package com.example;

                    import org.apache.commons.logging.Log;
                    import org.apache.commons.logging.LogFactory;

                    public class OrderService {
                        private static final Log logger = LogFactory.getLog(OrderService.class);

                        public void placeOrder(String id) {
                            logger.info("Placing " + id);
                        }
                    }
                    """,
            List.of("commons-logging:commons-logging:1.2", "org.slf4j:slf4j-api:2.0.17"));

    static final Fixture ORDER_SERVICE_LOG4J1 = new Fixture(
            "src/main/java/com/example/OrderService.java",
            """
                    package com.example;

                    import org.apache.log4j.Logger;

                    public class OrderService {
                        private static final Logger logger = Logger.getLogger(OrderService.class);

                        public void placeOrder(String id) {
                            logger.info("Placing " + id);
                        }
                    }
                    """,
            List.of("log4j:log4j:1.2.17"));
}
