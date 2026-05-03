package io.github.fiftieshousewife.recipes;

enum LoggerNames {

    LOG4J2_LOGGER("org.apache.logging.log4j.Logger"),
    LOG4J2_LOG_MANAGER("org.apache.logging.log4j.LogManager"),
    JUL_LOGGER("java.util.logging.Logger");

    private final String fqn;

    LoggerNames(final String fqn) {
        this.fqn = fqn;
    }

    String fqn() {
        return fqn;
    }
}
